package com.admision.lector_dbf.exam.service;

import com.admision.lector_dbf.evaluation.repository.EvaluationRepository;
import com.admision.lector_dbf.exam.model.ExamModels;
import com.admision.lector_dbf.exam.repository.ExamRepository;
import com.admision.lector_dbf.security.OrganizationAccess;
import com.admision.lector_dbf.shared.ApiException;
import com.admision.lector_dbf.shared.PageRequest;
import com.admision.lector_dbf.shared.PageResult;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.admision.lector_dbf.importer.model.ImportModels.SectionScore;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
public class ExamService {
    private final ExamRepository exams;
    private final EvaluationRepository evaluations;
    private final OrganizationAccess access;
    private final ObjectMapper json;

    public ExamService(ExamRepository exams, EvaluationRepository evaluations, OrganizationAccess access, ObjectMapper json) {
        this.exams = exams;
        this.evaluations = evaluations;
        this.access = access;
        this.json = json;
    }

    public ExamModels.Page<ExamModels.Exam> page(long organizationId, long evaluationId, String code,
            Long groupId, String status, int page, int size, String order, Authentication auth) {
        access.require(organizationId, auth);
        evaluations.require(organizationId, evaluationId);
        if (page < 0 || size < 1 || size > 100 || (long) page * size > Integer.MAX_VALUE)
            throw ApiException.badRequest("Paginación inválida");
        if (!status.isEmpty() && !List.of("RECIBIDA", "OBSERVADA", "VALIDADA", "ANULADA").contains(status))
            throw ApiException.badRequest("Estado inválido");
        if (!List.of("puntaje-desc", "puntaje-asc", "fecha-desc").contains(order))
            throw ApiException.badRequest("Orden inválido");
        return exams.page(organizationId, evaluationId, code, groupId, status, page, size, order);
    }

    public ExamModels.Exam find(long organizationId, long evaluationId, long sheetId, Authentication auth) {
        access.require(organizationId, auth);
        return exams.find(organizationId, evaluationId, sheetId).orElseThrow(() -> ApiException.notFound("Examen"));
    }

    public ExamModels.Explanation explanation(long organizationId, long evaluationId, long sheetId,
                                              Authentication auth) {
        access.require(organizationId, auth);
        ExamModels.ExplanationData data = exams.explanation(organizationId, evaluationId, sheetId)
                .orElseThrow(() -> ApiException.notFound("Resultado"));
        try {
            List<SectionScore> sections = json.readValue(data.sectionDetails(), new TypeReference<>() {});
            return new ExamModels.Explanation(data.sheetId(), data.formCode(), data.questions(),
                    data.keyVersion(), data.ruleVersion(), data.correctPoints(), data.incorrectPoints(),
                    data.blankPoints(), data.minimumScore(), data.sectionVersion(), data.score(), sections,
                    data.officialScore(), data.officialFormula());
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("El desglose de calificación no se pudo leer", exception);
        }
    }

    public PageResult<ExamModels.RankingDecision> decisions(long organizationId, long evaluationId,
                                                             long sheetId, int page, int size, Authentication auth) {
        access.require(organizationId, auth);
        new PageRequest(page, size);
        return exams.decisions(organizationId, evaluationId, sheetId, page, size);
    }
}
