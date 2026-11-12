package com.admision.lector_dbf.exam.service;

import com.admision.lector_dbf.audit.repository.AuditRepository;
import com.admision.lector_dbf.evaluation.model.EvaluationModels.Rule;
import com.admision.lector_dbf.evaluation.repository.EvaluationRepository;
import com.admision.lector_dbf.evaluation.repository.SectionConfigurationRepository;
import com.admision.lector_dbf.exam.model.ResultVersion;
import com.admision.lector_dbf.exam.model.ResultVersionDetail;
import com.admision.lector_dbf.importer.model.ImportModels.SectionScore;
import com.admision.lector_dbf.exam.repository.ExamRepository;
import com.admision.lector_dbf.exam.repository.ResultVersionRepository;
import com.admision.lector_dbf.importer.service.ScoringService;
import com.admision.lector_dbf.publication.repository.PublicationRepository;
import com.admision.lector_dbf.security.OrganizationAccess;
import com.admision.lector_dbf.shared.ApiException;
import com.admision.lector_dbf.shared.PageRequest;
import com.admision.lector_dbf.shared.PageResult;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class ResultVersionService {
    private final ResultVersionRepository versions;
    private final EvaluationRepository evaluations;
    private final SectionConfigurationRepository sections;
    private final ScoringService scoring;
    private final ExamRepository exams;
    private final AuditRepository audit;
    private final OrganizationAccess access;
    private final ObjectMapper json;
    private final PublicationRepository publications;

    public ResultVersionService(ResultVersionRepository versions, EvaluationRepository evaluations,
            SectionConfigurationRepository sections, ScoringService scoring, ExamRepository exams,
            AuditRepository audit, OrganizationAccess access, ObjectMapper json,
            PublicationRepository publications) {
        this.versions = versions; this.evaluations = evaluations; this.sections = sections;
        this.scoring = scoring; this.exams = exams; this.audit = audit; this.access = access; this.json = json;
        this.publications = publications;
    }

    public PageResult<ResultVersion> page(long org, long evaluation, long sheet, int page, int size,
                                           Authentication auth) {
        access.require(org, auth);
        evaluations.require(org, evaluation);
        new PageRequest(page, size);
        return versions.page(org, evaluation, sheet, page, size);
    }

    public ResultVersionDetail detail(long org, long evaluation, long sheet, long versionId,
                                      Authentication auth) {
        access.require(org, auth);
        var source = versions.detail(org, evaluation, sheet, versionId)
                .orElseThrow(() -> ApiException.notFound("Versión de resultado"));
        try {
            List<String> answers = json.readValue(source.responses(), new TypeReference<>() {});
            List<String> key = json.readValue(source.key(), new TypeReference<>() {});
            List<SectionScore> details = json.readValue(source.sections(), new TypeReference<>() {});
            return new ResultVersionDetail(source.version(), answers, key, source.correctPoints(),
                    source.incorrectPoints(), source.blankPoints(), details);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("No se pudo leer la versión de resultado", exception);
        }
    }

    @Transactional
    public int recalculate(long org, long evaluation, String reason, Authentication auth) {
        long userId = access.require(org, auth, "ADMINISTRADOR", "OPERADOR").id();
        publications.assertMutable(org, evaluation);
        String status = evaluations.require(org, evaluation).status();
        if (!List.of("EN_PROCESO", "CERRADA").contains(status))
            throw ApiException.conflict("Solo se pueden recalcular evaluaciones en proceso o cerradas");
        if (reason == null || reason.isBlank()) throw ApiException.badRequest("Indica el motivo del recálculo");
        List<Long> sheets = versions.sheetIds(evaluation);
        if (sheets.isEmpty()) throw ApiException.conflict("No hay resultados para recalcular");
        Rule rule = evaluations.latestRule(evaluation)
                .orElseThrow(() -> ApiException.conflict("Falta la regla de calificación"));
        try {
            for (long sheet : sheets) recalculateOne(org, evaluation, sheet, reason.trim(), userId, rule);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("No se pudieron leer las respuestas guardadas", exception);
        }
        exams.recalculateRanking(evaluation);
        audit.record(org, evaluation, userId, "RESULTADOS_RECALCULADOS", "EVALUACION", evaluation,
                "{\"examenes\":" + sheets.size() + "}");
        return sheets.size();
    }

    private void recalculateOne(long org, long evaluation, long sheet, String reason, long userId, Rule rule)
            throws JsonProcessingException {
        ResultVersionRepository.Source source = versions.source(org, evaluation, sheet)
                .orElseThrow(() -> ApiException.notFound("Resultado"));
        var key = evaluations.latestKey(source.formId())
                .orElseThrow(() -> ApiException.conflict("Falta la clave del tema"));
        var section = sections.latest(source.formId()).orElse(null);
        List<String> answers = json.readValue(source.answers(), new TypeReference<>() {});
        List<String> keyAnswers = json.readValue(key.answers(), new TypeReference<>() {});
        var score = scoring.score(answers, keyAnswers, rule, section == null ? List.of() : section.sections());
        versions.replace(source, key.id(), rule.id(), section == null ? null : section.id(),
                json.writeValueAsString(score.sections()), score, reason, userId);
    }
}
