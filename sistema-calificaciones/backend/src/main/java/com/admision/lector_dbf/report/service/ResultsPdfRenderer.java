package com.admision.lector_dbf.report.service;

import com.admision.lector_dbf.exam.model.ExamModels.Exam;
import com.admision.lector_dbf.report.model.ResultsReport;
import com.admision.lector_dbf.shared.ResultLanguage;
import com.lowagie.text.*;
import com.lowagie.text.pdf.*;
import org.springframework.stereotype.Component;
import java.awt.Color;
import java.util.*;
import java.util.List;

@Component
public class ResultsPdfRenderer {
    public byte[] render(ResultsReport report) {
        var evaluation=report.evaluation();
        boolean grouped=report.policy()!=null && "GRUPO".equals(report.policy().scope()) && !report.groups().isEmpty();
        String state=switch (evaluation.status()) {
            case "PUBLICADA" -> "Resultados publicados";
            case "ARCHIVADA" -> "Resultados archivados";
            default -> "Documento en preparación - "+ResultLanguage.state(evaluation.status());
        };
        try (InstitutionalPdf pdf=new InstitutionalPdf(report.organization(),report.logo(),"Resultados de evaluación",evaluation.code()+" | "+state,evaluation.code())) {
            Document document=pdf.document();
            if (grouped) {
                boolean first=true;
                for (var group:report.groups()) {
                    if (!first) document.newPage(); first=false;
                    introduction(document,report,group.name(),group.capacity());
                    resultTable(document,report.exams().stream().filter(e -> Objects.equals(e.groupId(),group.id())).toList(),false,report.officialScoring(),evaluation.type(),
                            groupLabel(evaluation.groupType())+": "+group.name()+" | Vacantes: "+(group.capacity()==null ? "Sin definir" : group.capacity()));
                }
                List<Exam> unassigned=report.exams().stream().filter(e -> e.groupId()==null).toList();
                if (!unassigned.isEmpty()) { document.newPage(); introduction(document,report,"Sin grupo",null); resultTable(document,unassigned,false,report.officialScoring(),evaluation.type(),"Sin grupo"); }
            } else {
                introduction(document,report,null,report.policy()==null ? null : report.policy().globalCapacity());
                List<Exam> ranked=report.exams().stream().sorted(Comparator.comparing(Exam::position,Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(Exam::rankingScore,Comparator.nullsLast(Comparator.reverseOrder())).thenComparingLong(Exam::id)).toList();
                resultTable(document,ranked,true,report.officialScoring(),evaluation.type(),"Ranking general de la evaluación");
            }
            InstitutionalPdf.signature(document,report.organization());
            return pdf.finish();
        }
    }

    private void introduction(Document document,ResultsReport report,String group,Integer capacity) {
        Paragraph title=new Paragraph(report.evaluation().name(),InstitutionalPdf.font(16,true)); title.setSpacingAfter(8); document.add(title);
        String details="Fecha de evaluación: "+(report.evaluation().date()==null ? "Sin fecha" : report.evaluation().date());
        if (group!=null) details+=" | "+groupLabel(report.evaluation().groupType())+": "+group;
        if (capacity!=null) details+=" | Vacantes: "+capacity;
        Paragraph info=new Paragraph(details,InstitutionalPdf.font(10,true)); info.setSpacingAfter(8); document.add(info);
    }

    private void resultTable(Document document,List<Exam> exams,boolean showGroup,boolean officialScoring,String type,String section) {
        float scoreWidth = officialScoring ? 1.35f : 1f;
        PdfPTable table=InstitutionalPdf.table(showGroup
                ? new float[]{.4f,1.1f,2.7f,1.35f,.65f,scoreWidth,.85f,1.5f}
                : new float[]{.45f,1.05f,3.45f,.65f,scoreWidth,.85f,1.5f});
        table.setSplitRows(false);
        List<String> headings=new ArrayList<>(List.of("N°","Código","Participante"));
        if (showGroup) headings.add("Grupo");
        headings.addAll(List.of("Tema",officialScoring ? "Nota oficial" : "Puntaje","Puesto","Condición"));
        PdfPCell group=InstitutionalPdf.cell(section,true); group.setColspan(headings.size()); table.addCell(group);
        for (String heading:headings) table.addCell(InstitutionalPdf.cell(heading,true));
        table.setHeaderRows(2);
        int index=0;
        for (Exam exam:exams) {
            index++;
            List<String> values=new ArrayList<>(List.of(Integer.toString(index),exam.participantCode(),exam.participantName()));
            if (showGroup) values.add(exam.groupName());
            var score = officialScoring ? exam.officialScore() : exam.score();
            values.add(exam.formCode()); values.add(score == null ? "-" : score.toPlainString());
            values.add(exam.position()==null ? "-" : exam.position().toString()); values.add(condition(exam,type));
            for (int column=0;column<values.size();column++) {
                PdfPCell cell=InstitutionalPdf.cell(values.get(column),false);
                if (index%2==0) cell.setBackgroundColor(new Color(247,249,248));
                if (column!=2 && (!showGroup || column!=3)) cell.setHorizontalAlignment(Element.ALIGN_CENTER);
                table.addCell(cell);
            }
        }
        if (exams.isEmpty()) { PdfPCell empty=InstitutionalPdf.cell("No hay exámenes registrados en este grupo.",false); empty.setColspan(headings.size()); table.addCell(empty); }
        document.add(table);
        document.add(new Paragraph("Total: "+exams.size()+" exámenes. Las hojas observadas o anuladas no participan del ranking.",InstitutionalPdf.font(8,false)));
    }
    private String condition(Exam exam,String type) {
        if (!"VALIDADA".equals(exam.status())) return switch(exam.status()) { case "ANULADA" -> "ANULADO"; case "OBSERVADA" -> "OBSERVADO"; default -> "RECIBIDO"; };
        if (exam.passed()==null) return "SIN DECISIÓN";
        var language=ResultLanguage.forType(type);
        return (exam.passed() ? language.positive() : language.negative()).toUpperCase(java.util.Locale.ROOT);
    }
    private String groupLabel(String type) {
        return switch(type) { case "CARRERA" -> "Carrera"; case "SEDE" -> "Sede"; case "PROGRAMA" -> "Programa"; case "CATEGORIA" -> "Categoría"; default -> "Grupo"; };
    }
}
