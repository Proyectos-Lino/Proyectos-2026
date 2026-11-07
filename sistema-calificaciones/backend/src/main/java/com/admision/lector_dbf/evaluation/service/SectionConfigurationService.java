package com.admision.lector_dbf.evaluation.service;

import com.admision.lector_dbf.evaluation.model.EvaluationModels.Form;
import com.admision.lector_dbf.evaluation.model.ScoringModels.SectionConfiguration;
import com.admision.lector_dbf.evaluation.model.ScoringRequests.SaveSections;
import com.admision.lector_dbf.evaluation.model.ScoringRequests.SectionInput;
import com.admision.lector_dbf.evaluation.repository.EvaluationRepository;
import com.admision.lector_dbf.evaluation.repository.SectionConfigurationRepository;
import com.admision.lector_dbf.publication.repository.PublicationRepository;
import com.admision.lector_dbf.audit.repository.AuditRepository;
import com.admision.lector_dbf.security.OrganizationAccess;
import com.admision.lector_dbf.shared.ApiException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SectionConfigurationService {
    private final SectionConfigurationRepository sections;
    private final EvaluationRepository evaluations;
    private final OrganizationAccess access;
    private final PublicationRepository publications;
    private final AuditRepository audit;

    public SectionConfigurationService(SectionConfigurationRepository sections,
                                       EvaluationRepository evaluations, OrganizationAccess access,
                                       PublicationRepository publications, AuditRepository audit) {
        this.sections = sections; this.evaluations = evaluations; this.access = access;
        this.publications = publications; this.audit = audit;
    }

    public SectionConfiguration current(long org, long evaluationId, long formId, Authentication auth) {
        access.require(org, auth);
        form(org, evaluationId, formId);
        return sections.latest(formId).orElseThrow(() -> ApiException.notFound("Configuración de secciones"));
    }

    @Transactional
    public SectionConfiguration save(long org, long evaluationId, long formId,
                                     SaveSections request, Authentication auth) {
        long userId = access.require(org, auth, "ADMINISTRADOR", "OPERADOR").id();
        publications.assertMutable(org, evaluationId);
        Form form = form(org, evaluationId, formId);
        validate(request.sections(), form.questions());
        SectionConfiguration saved = sections.save(formId, request.sections(), userId);
        audit.record(org, evaluationId, userId, "SECCIONES_CREADAS", "SECCIONES", saved.id(),
                "{\"temaId\":" + formId + "}");
        return saved;
    }

    private Form form(long org, long evaluationId, long formId) {
        evaluations.require(org, evaluationId);
        return evaluations.form(evaluationId, formId).orElseThrow(() -> ApiException.notFound("Tema"));
    }

    private void validate(List<SectionInput> inputs, int questions) {
        if (inputs.size() > questions) throw ApiException.badRequest("Hay más secciones que preguntas");
        int nextQuestion = 1;
        Set<String> codes = new HashSet<>();
        for (SectionInput input : inputs) {
            if (input.fromQuestion() != nextQuestion || input.toQuestion() < input.fromQuestion()
                    || input.toQuestion() > questions)
                throw ApiException.badRequest("Las secciones deben cubrir las preguntas en orden y sin solaparse");
            if (input.code().length() > 40 || input.name().length() > 120
                    || !codes.add(input.code().trim().toUpperCase()))
                throw ApiException.badRequest("Revisa los códigos y nombres de las secciones");
            nextQuestion = input.toQuestion() + 1;
        }
        if (!inputs.isEmpty() && nextQuestion != questions + 1)
            throw ApiException.badRequest("Las secciones deben cubrir todas las preguntas del tema");
    }
}
