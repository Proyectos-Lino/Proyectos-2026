package com.admision.lector_dbf.report.service;

import com.admision.lector_dbf.account.repository.OrganizationProfileRepository;
import com.admision.lector_dbf.publication.service.PublicationService;
import com.admision.lector_dbf.shared.ResultLanguage;
import com.lowagie.text.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Isolation;

@Service
public class IndividualPdfService {
    private final PublicationService publications;
    private final OrganizationProfileRepository organizations;
    public IndividualPdfService(PublicationService publications,OrganizationProfileRepository organizations) {
        this.publications=publications; this.organizations=organizations;
    }
    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public byte[] result(String token) {
        var context=publications.report(token);
        var result=context.result();
        var org=organizations.get(context.organizationId());
        byte[] logo=organizations.logo(org.id()).map(l -> l.content()).orElse(null);
        try (InstitutionalPdf pdf=new InstitutionalPdf(org,logo,"Constancia de resultado",context.evaluationCode()+" | Resultado publicado",context.evaluationCode())) {
            Document document=pdf.document();
            Paragraph title=new Paragraph(result.evaluation(),InstitutionalPdf.font(18,true)); title.setSpacingAfter(18); document.add(title);
            document.add(new Paragraph(result.participantName(),InstitutionalPdf.font(16,true)));
            document.add(new Paragraph("Código del participante: "+result.participantCode(),InstitutionalPdf.font(11,false)));
            document.add(new Paragraph("Tema: "+result.formCode()+" | Grupo: "+InstitutionalPdf.text(context.groupName()),InstitutionalPdf.font(10,false)));
            if (context.evaluationDate()!=null) document.add(new Paragraph("Fecha de evaluación: "+context.evaluationDate(),InstitutionalPdf.font(10,false)));
            var table=InstitutionalPdf.table(2,1);
            table.addCell(InstitutionalPdf.cell("Concepto",true)); table.addCell(InstitutionalPdf.cell("Resultado",true));
            if (result.score()!=null) metric(table,"Puntaje",result.score().toPlainString());
            if (result.position()!=null) metric(table,"Puesto",result.position().toString());
            var language=ResultLanguage.forType(context.evaluationType());
            if (result.passed()!=null) metric(table,language.heading(),result.passed()?language.positive():language.negative());
            if (result.correct()!=null) metric(table,"Respuestas correctas",result.correct().toString());
            if (result.incorrect()!=null) metric(table,"Respuestas incorrectas",result.incorrect().toString());
            if (result.blank()!=null) metric(table,"Respuestas en blanco",result.blank().toString());
            if (result.score()!=null || result.position()!=null || result.passed()!=null || result.correct()!=null) document.add(table);
            document.add(new Paragraph("Documento emitido con los datos que la organización ha habilitado para la consulta individual.",InstitutionalPdf.font(9,false)));
            InstitutionalPdf.signature(document,org);
            return pdf.finish();
        }
    }
    private void metric(com.lowagie.text.pdf.PdfPTable table,String label,String value) {
        table.addCell(InstitutionalPdf.cell(label,false)); table.addCell(InstitutionalPdf.cell(value,true));
    }
}
