package com.admision.lector_dbf.evaluation.service;

import com.admision.lector_dbf.evaluation.model.ScoringModels.RankingPolicy;
import com.admision.lector_dbf.evaluation.model.ScoringRequests.SaveRankingPolicy;
import com.admision.lector_dbf.evaluation.repository.EvaluationRepository;
import com.admision.lector_dbf.evaluation.repository.RankingPolicyRepository;
import com.admision.lector_dbf.exam.repository.ExamRepository;
import com.admision.lector_dbf.publication.repository.PublicationRepository;
import com.admision.lector_dbf.audit.repository.AuditRepository;
import com.admision.lector_dbf.security.OrganizationAccess;
import com.admision.lector_dbf.shared.ApiException;
import java.util.Set;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RankingPolicyService {
    private final RankingPolicyRepository policies;
    private final EvaluationRepository evaluations;
    private final ExamRepository exams;
    private final OrganizationAccess access;
    private final PublicationRepository publications;
    private final AuditRepository audit;

    public RankingPolicyService(RankingPolicyRepository policies, EvaluationRepository evaluations,
                                ExamRepository exams, OrganizationAccess access,
                                PublicationRepository publications, AuditRepository audit) {
        this.policies = policies; this.evaluations = evaluations; this.exams = exams; this.access = access;
        this.publications = publications; this.audit = audit;
    }

    public RankingPolicy current(long org, long evaluationId, Authentication auth) {
        access.require(org, auth);
        evaluations.require(org, evaluationId);
        return policies.latest(evaluationId).orElseThrow(() -> ApiException.notFound("Política de resultados"));
    }

    @Transactional
    public RankingPolicy save(long org, long evaluationId, SaveRankingPolicy request, Authentication auth) {
        long userId = access.require(org, auth, "ADMINISTRADOR", "OPERADOR").id();
        publications.assertMutable(org, evaluationId);
        evaluations.require(org, evaluationId);
        String scope = request.scope().trim().toUpperCase();
        String mode = request.approvalMode().trim().toUpperCase();
        String tie = request.tieBreak().trim().toUpperCase();
        if (!Set.of("GLOBAL", "GRUPO").contains(scope)
                || !Set.of("NINGUNA", "MINIMA", "VACANTES", "AMBAS", "VACANTES_O_MINIMA").contains(mode)
                || !Set.of("COMPARTIR_PUESTO", "CORRECTAS", "MENOS_INCORRECTAS", "CODIGO").contains(tie))
            throw ApiException.badRequest("Revisa el alcance, la aprobación y el desempate");
        boolean usesCapacity = Set.of("VACANTES", "AMBAS", "VACANTES_O_MINIMA").contains(mode);
        if ("GLOBAL".equals(scope) && usesCapacity && request.globalCapacity() == null)
            throw ApiException.badRequest("Indica el número de vacantes para el ranking global");
        RankingPolicy policy = policies.save(evaluationId, scope, mode, tie,
                "GLOBAL".equals(scope) ? request.globalCapacity() : null, userId);
        exams.recalculateRanking(evaluationId);
        audit.record(org, evaluationId, userId, "POLITICA_CREADA", "POLITICA", policy.id(), "{}");
        return policy;
    }
}
