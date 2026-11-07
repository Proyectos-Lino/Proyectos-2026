package com.admision.lector_dbf.report.repository;

import com.admision.lector_dbf.evaluation.repository.EvaluationRepository;
import com.admision.lector_dbf.exam.repository.ExamRepository;
import com.admision.lector_dbf.report.model.ResultsReport;
import com.admision.lector_dbf.account.repository.OrganizationProfileRepository;
import com.admision.lector_dbf.evaluation.repository.RankingPolicyRepository;
import org.springframework.stereotype.Repository;

@Repository
public class ReportRepository {
    private final EvaluationRepository evaluations;
    private final ExamRepository exams;
    private final OrganizationProfileRepository organizations;
    private final RankingPolicyRepository policies;

    public ReportRepository(EvaluationRepository evaluations, ExamRepository exams,OrganizationProfileRepository organizations,RankingPolicyRepository policies) {
        this.evaluations = evaluations;
        this.exams = exams;
        this.organizations=organizations; this.policies=policies;
    }

    public ResultsReport results(long organizationId, long evaluationId) {
        return new ResultsReport(evaluations.require(organizationId, evaluationId),
                exams.allForPdf(organizationId, evaluationId),organizations.get(organizationId),
                organizations.logo(organizationId).map(l -> l.content()).orElse(null),policies.latest(evaluationId).orElse(null),evaluations.groups(evaluationId));
    }
}
