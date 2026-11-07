package com.admision.lector_dbf.report.service;

import com.admision.lector_dbf.evaluation.repository.EvaluationRepository;
import com.admision.lector_dbf.account.repository.OrganizationProfileRepository;
import com.admision.lector_dbf.shared.ResultLanguage;
import com.admision.lector_dbf.report.model.AnalyticsModels.*;
import com.lowagie.text.*;
import com.lowagie.text.pdf.*;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;

@Service
public class AnalyticsPdfService {
    private final AnalyticsService analytics;
    private final EvaluationRepository evaluations;
    private final OrganizationProfileRepository organizations;
    public AnalyticsPdfService(AnalyticsService analytics, EvaluationRepository evaluations,OrganizationProfileRepository organizations) {
        this.analytics=analytics; this.evaluations=evaluations;
        this.organizations=organizations;
    }

    public byte[] summary(Filter filter, Authentication auth) {
        Dashboard dashboard=analytics.dashboard(filter,auth);
        var evaluation=evaluations.require(filter.organizationId(),filter.evaluationId());
        Summary s=dashboard.summary();
        var organization=organizations.get(filter.organizationId());
        byte[] logo=organizations.logo(filter.organizationId()).map(l -> l.content()).orElse(null);
        var language=ResultLanguage.forType(evaluation.type());
        try (InstitutionalPdf pdf=new InstitutionalPdf(organization,logo,"Informe de análisis",evaluation.code()+" | "+ResultLanguage.state(evaluation.status()),evaluation.code())) {
            Document document=pdf.document();
            document.add(new Paragraph(evaluation.name(),new Font(Font.HELVETICA,19,Font.BOLD)));
            document.add(new Paragraph("Informe de resultados · "+evaluation.code(),new Font(Font.HELVETICA,11)));
            String group=filter.groupId()==null ? "Todos" : filter.groupId()==0 ? "Sin grupo"
                    : evaluations.groups(filter.evaluationId()).stream().filter(g -> g.id()==filter.groupId())
                    .map(g -> g.name()).findFirst().orElse("—");
            String form=filter.formId()==null ? "Todos" : evaluations.form(filter.evaluationId(),filter.formId()).orElseThrow().code();
            document.add(new Paragraph("Grupo: "+group+" · Tema: "+form,new Font(Font.HELVETICA,10)));
            document.add(new Paragraph(" "));
            PdfPTable metrics=new PdfPTable(2); metrics.setWidthPercentage(100);
            metric(metrics,"Inscripciones en el filtro",s.registrations());
            metric(metrics,"Exámenes / resultados válidos",s.sheets()+" / "+s.scored());
            metric(metrics,"Observados / anulados / recibidos",s.observed()+" / "+s.annulled()+" / "+s.received());
            metric(metrics,language.positivePlural()+" / "+language.negativePlural().toLowerCase()+" / sin decisión",s.passed()+" / "+s.failed()+" / "+s.undecided());
            metric(metrics,"Promedio / mediana",number(s.average())+" / "+number(s.median()));
            metric(metrics,"Puntaje mínimo / máximo",number(s.minimum())+" / "+number(s.maximum()));
            metric(metrics,"Desviación estándar poblacional",number(s.deviation()));
            document.add(metrics);
            document.add(new Paragraph("Distribución de puntajes",new Font(Font.HELVETICA,13,Font.BOLD)));
            document.add(new Paragraph("Los intervalos incluyen el límite inferior; el último también incluye el superior.",new Font(Font.HELVETICA,9)));
            PdfPTable bins=new PdfPTable(3); bins.setWidthPercentage(100); bins.setSpacingBefore(10);
            bins.setHeaderRows(1);
            for (String heading:List.of("Desde","Hasta","Exámenes válidos")) cell(bins,heading,true);
            for (ScoreBin bin:dashboard.distribution()) {
                cell(bins,number(bin.lower()),false); cell(bins,number(bin.upper()),false); cell(bins,Long.toString(bin.count()),false);
            }
            if (dashboard.distribution().isEmpty()) {
                cell(bins,"Sin resultados válidos",false); cell(bins,"—",false); cell(bins,"0",false);
            }
            document.add(bins);
            document.add(new Paragraph("Criterios: los indicadores de puntaje y decisión consideran solo resultados de exámenes válidos. "
                    +"Los observados y anulados se cuentan por separado. Sin decisión indica que el resultado aún no se determinó o que no aplica. "
                    +"Al filtrar por tema, las inscripciones corresponden a personas con hoja en ese tema.",new Font(Font.HELVETICA,9)));
            InstitutionalPdf.signature(document,organization);
            return pdf.finish();
        }
    }

    private void metric(PdfPTable table,String label,Object value) {
        cell(table,label,false); cell(table,value.toString(),true);
    }
    private String number(BigDecimal value) { return value==null ? "—" : value.stripTrailingZeros().toPlainString(); }
    private void cell(PdfPTable table,String value,boolean heading) {
        table.addCell(InstitutionalPdf.cell(value,heading));
    }
}
