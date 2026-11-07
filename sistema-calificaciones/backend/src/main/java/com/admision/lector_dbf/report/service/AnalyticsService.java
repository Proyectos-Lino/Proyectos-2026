package com.admision.lector_dbf.report.service;

import com.admision.lector_dbf.evaluation.repository.EvaluationRepository;
import com.admision.lector_dbf.report.model.AnalyticsModels.*;
import com.admision.lector_dbf.report.repository.AnalyticsRepository;
import com.admision.lector_dbf.security.OrganizationAccess;
import com.admision.lector_dbf.shared.ApiException;
import com.admision.lector_dbf.shared.PageRequest;
import com.admision.lector_dbf.shared.PageResult;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.Set;

@Service
public class AnalyticsService {
    private final AnalyticsRepository reports;
    private final EvaluationRepository evaluations;
    private final OrganizationAccess access;

    public AnalyticsService(AnalyticsRepository reports, EvaluationRepository evaluations, OrganizationAccess access) {
        this.reports=reports; this.evaluations=evaluations; this.access=access;
    }

    public void validate(Filter filter, Authentication auth) {
        access.require(filter.organizationId(),auth);
        evaluations.require(filter.organizationId(),filter.evaluationId());
        if (filter.groupId()!=null && (filter.groupId()<0 || (filter.groupId()>0 && !reports.groupExists(filter))))
            throw ApiException.badRequest("El grupo no pertenece a la evaluación");
        if (filter.formId()!=null && evaluations.form(filter.evaluationId(),filter.formId()).isEmpty())
            throw ApiException.badRequest("El tema no pertenece a la evaluación");
    }

    @Transactional(readOnly=true, isolation=Isolation.REPEATABLE_READ)
    public Dashboard dashboard(Filter filter, Authentication auth) {
        validate(filter,auth);
        return new Dashboard(reports.summary(filter),reports.distribution(filter),OffsetDateTime.now());
    }

    @Transactional(readOnly=true, isolation=Isolation.REPEATABLE_READ)
    public PageResult<GroupStats> groups(Filter filter, String query, String mode, int page, int size, Authentication auth) {
        validate(filter,auth); validateGroupMode(mode); new PageRequest(page,size);
        return reports.groups(filter,query,mode,page,size);
    }

    @Transactional(readOnly=true, isolation=Isolation.REPEATABLE_READ)
    public PageResult<QuestionStats> questions(Filter filter, String query, String mode, String order,
                                               int page, int size, Authentication auth) {
        validate(filter,auth); validateQuestionOptions(mode,order); new PageRequest(page,size);
        return reports.questions(filter,query,mode,order,page,size);
    }

    public void validateGroupMode(String mode) {
        if (!Set.of("","CON_RESULTADOS","SIN_RESULTADOS").contains(mode))
            throw ApiException.badRequest("Filtro de grupos inválido");
    }

    public void validateQuestionOptions(String mode, String order) {
        if (!Set.of("","CON_ERRORES","SIN_ERRORES").contains(mode) || !Set.of("errores","pregunta").contains(order))
            throw ApiException.badRequest("Filtro u orden de preguntas inválido");
    }
}
