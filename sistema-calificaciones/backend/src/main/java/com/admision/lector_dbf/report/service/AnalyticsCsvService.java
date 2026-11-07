package com.admision.lector_dbf.report.service;

import com.admision.lector_dbf.report.model.AnalyticsModels.Filter;
import com.admision.lector_dbf.report.repository.AnalyticsRepository;
import com.admision.lector_dbf.evaluation.repository.EvaluationRepository;
import com.admision.lector_dbf.shared.ResultLanguage;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;

@Service
public class AnalyticsCsvService {
    private final AnalyticsService analytics;
    private final AnalyticsRepository repository;
    private final EvaluationRepository evaluations;
    public AnalyticsCsvService(AnalyticsService analytics, AnalyticsRepository repository,EvaluationRepository evaluations) {
        this.analytics=analytics; this.repository=repository;
        this.evaluations=evaluations;
    }

    @Transactional(readOnly=true, isolation=Isolation.REPEATABLE_READ)
    public void results(Filter filter, Authentication auth, OutputStream output) throws IOException {
        analytics.validate(filter,auth);
        OutputStreamWriter writer=start(output);
        CsvReportWriter csv=new CsvReportWriter(writer);
        var language=ResultLanguage.forType(evaluations.require(filter.organizationId(),filter.evaluationId()).type());
        csv.row("Hoja ID","Código","Participante","Grupo","Tema","Estado","Correctas","Incorrectas","Blancas",
                "Puntaje","Puesto",language.heading(),"Versión clave","Versión regla","Versión puntaje");
        repository.eachResult(filter,row -> csv.row(row.sheetId(),row.participantCode(),row.participantName(),
                row.groupName(),row.formCode(),row.status(),row.correct(),row.incorrect(),row.blank(),row.score(),
                row.position(),row.passed(),row.keyVersion(),row.ruleVersion(),row.scoreVersion()));
        writer.flush();
    }

    @Transactional(readOnly=true, isolation=Isolation.REPEATABLE_READ)
    public void groups(Filter filter, String query, String mode, Authentication auth, OutputStream output) throws IOException {
        analytics.validate(filter,auth); analytics.validateGroupMode(mode);
        var language=ResultLanguage.forType(evaluations.require(filter.organizationId(),filter.evaluationId()).type());
        OutputStreamWriter writer=start(output);
        CsvReportWriter csv=new CsvReportWriter(writer);
        csv.row("Grupo ID","Código","Grupo","Inscritos","Exámenes","Resultados válidos","Observados","Anulados",
                language.positivePlural(),language.negativePlural(),"Sin decisión","Promedio","Mínimo","Máximo");
        repository.eachGroup(filter,query,mode,row -> csv.row(row.groupId(),row.code(),row.name(),row.registrations(),
                row.sheets(),row.scored(),row.observed(),row.annulled(),row.passed(),row.failed(),row.undecided(),
                row.average(),row.minimum(),row.maximum()));
        writer.flush();
    }

    @Transactional(readOnly=true, isolation=Isolation.REPEATABLE_READ)
    public void questions(Filter filter, String query, String mode, String order, Authentication auth, OutputStream output) throws IOException {
        analytics.validate(filter,auth); analytics.validateQuestionOptions(mode,order);
        OutputStreamWriter writer=start(output);
        CsvReportWriter csv=new CsvReportWriter(writer);
        csv.row("Tema ID","Tema","Versión clave","Pregunta","Respuestas","Correctas","Incorrectas","Blancas","Porcentaje incorrectas");
        repository.eachQuestion(filter,query,mode,order,row -> csv.row(row.formId(),row.formCode(),row.keyVersion(),
                row.question(),row.responses(),row.correct(),row.incorrect(),row.blank(),row.incorrectPercent()));
        writer.flush();
    }

    private OutputStreamWriter start(OutputStream output) throws IOException {
        OutputStreamWriter writer=new OutputStreamWriter(output,StandardCharsets.UTF_8);
        writer.write('\uFEFF');
        return writer;
    }
}
