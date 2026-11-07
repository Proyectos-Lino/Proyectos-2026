package com.admision.lector_dbf.evaluation.service;

import com.admision.lector_dbf.evaluation.model.EvaluationModels.*;
import com.admision.lector_dbf.evaluation.model.EvaluationRequests.*;
import com.admision.lector_dbf.evaluation.repository.EvaluationRepository;
import com.admision.lector_dbf.evaluation.repository.RankingPolicyRepository;
import com.admision.lector_dbf.exam.repository.ExamRepository;
import com.admision.lector_dbf.publication.repository.PublicationRepository;
import com.admision.lector_dbf.audit.repository.AuditRepository;
import com.admision.lector_dbf.security.OrganizationAccess;
import com.admision.lector_dbf.shared.ApiException;
import com.admision.lector_dbf.shared.PageRequest;
import com.admision.lector_dbf.shared.PageResult;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

@Service
public class EvaluationService {
    private final EvaluationRepository evaluations;
    private final OrganizationAccess access;
    private final ObjectMapper json;
    private final ExamRepository exams;
    private final RankingPolicyRepository policies;
    private final PublicationRepository publications;
    private final AuditRepository audit;

    public EvaluationService(EvaluationRepository evaluations, OrganizationAccess access, ObjectMapper json,
                             ExamRepository exams, RankingPolicyRepository policies,
                             PublicationRepository publications, AuditRepository audit) {
        this.evaluations = evaluations;
        this.access = access;
        this.json = json;
        this.exams = exams;
        this.policies = policies;
        this.publications = publications;
        this.audit = audit;
    }

    public List<Evaluation> list(long org, Authentication auth) {
        access.require(org, auth);
        return evaluations.list(org);
    }

    public PageResult<Overview> page(long org, String query, String status, int page, int size, Authentication auth) {
        access.require(org, auth);
        new PageRequest(page, size);
        if (!status.isEmpty() && !List.of("BORRADOR", "CONFIGURADA", "EN_PROCESO", "CERRADA", "PUBLICADA", "ARCHIVADA").contains(status))
            throw ApiException.badRequest("Estado inválido");
        return evaluations.page(org, query, status, page, size);
    }

    public Overview overview(long org, long id, Authentication auth) {
        access.require(org, auth);
        return evaluations.overview(org, id);
    }

    public Statistics statistics(long org, Authentication auth) {
        access.require(org, auth);
        return evaluations.statistics(org);
    }

    @Transactional
    public Evaluation create(long org, CreateEvaluation request, Authentication auth) {
        long userId = access.require(org, auth, "ADMINISTRADOR", "OPERADOR").id();
        long id = evaluations.create(org, request.code().trim(), request.name().trim(),
                request.description(), request.type().trim(), request.date(), groupType(request.groupType()));
        policies.createDefault(id, request.type(), userId);
        return evaluations.require(org, id);
    }

    public Evaluation find(long org, long id, Authentication auth) {
        access.require(org, auth);
        return evaluations.require(org, id);
    }

    @Transactional
    public Evaluation update(long org, long id, UpdateEvaluation request, Authentication auth) {
        access.require(org, auth, "ADMINISTRADOR", "OPERADOR");
        Evaluation current = evaluations.require(org, id);
        String requestedGroupType = request.groupType() == null ? current.groupType() : groupType(request.groupType());
        if (evaluations.update(org, id, request.name().trim(), request.description(), request.date(), requestedGroupType) == 0)
            throw ApiException.conflict("Solo se puede modificar una evaluación en preparación");
        return evaluations.require(org, id);
    }

    @Transactional
    public Evaluation status(long org, long id, ChangeStatus request, Authentication auth) {
        long userId = access.require(org, auth, "ADMINISTRADOR", "OPERADOR").id();
        publications.lock(org, id);
        Evaluation current = evaluations.require(org, id);
        if ("PUBLICADA".equals(request.status()))
            throw ApiException.conflict("Aprueba y publica los resultados desde la sección Publicación");
        Map<String, List<String>> transitions = Map.of(
                "BORRADOR", List.of("CONFIGURADA"),
                "CONFIGURADA", List.of("EN_PROCESO", "BORRADOR"),
                "EN_PROCESO", List.of("CERRADA"),
                "CERRADA", List.of("EN_PROCESO"),
                "PUBLICADA", List.of("ARCHIVADA"),
                "ARCHIVADA", List.of());
        if (!transitions.getOrDefault(current.status(), List.of()).contains(request.status()))
            throw ApiException.conflict("Cambio de estado no permitido");
        if ("CERRADA".equals(current.status()) && publications.policy(id).approvedAt() != null)
            throw ApiException.conflict("Los resultados aprobados ya no pueden reabrirse");
        evaluations.status(org, id, request.status());
        audit.record(org, id, userId, "ESTADO_CAMBIADO", "EVALUACION", id,
                "{\"estado\":\"" + request.status() + "\"}");
        return evaluations.require(org, id);
    }

    public List<Group> groups(long org, long id, Authentication auth) {
        access.require(org, auth);
        evaluations.require(org, id);
        return evaluations.groups(id);
    }

    @Transactional
    public void updateCapacity(long org, long id, long groupId, UpdateCapacity request, Authentication auth) {
        access.require(org, auth, "ADMINISTRADOR", "OPERADOR");
        publications.assertMutable(org, id);
        evaluations.require(org, id);
        if (evaluations.updateCapacity(id, groupId, request.capacity()) == 0) throw ApiException.notFound("Grupo");
        exams.recalculateRanking(id);
    }

    @Transactional
    public void updateGroup(long org, long id, long groupId, UpdateGroup request, Authentication auth) {
        access.require(org, auth, "ADMINISTRADOR", "OPERADOR");
        publications.assertMutable(org, id);
        evaluations.require(org, id);
        if (evaluations.updateGroup(id, groupId, request.code().trim(), request.name().trim(), request.capacity()) == 0)
            throw ApiException.notFound("Grupo");
        exams.recalculateRanking(id);
    }

    private String groupType(String value) {
        String type = value == null || value.isBlank() ? "GRUPO" : value.trim().toUpperCase();
        if (!List.of("GRUPO", "CARRERA", "SEDE", "CATEGORIA", "PROGRAMA").contains(type))
            throw ApiException.badRequest("Tipo de grupo inválido");
        return type;
    }

    @Transactional
    public long createGroup(long org, long id, CreateGroup request, Authentication auth) {
        access.require(org, auth, "ADMINISTRADOR", "OPERADOR");
        publications.assertMutable(org, id);
        evaluations.require(org, id);
        return evaluations.createGroup(id, request.code().trim(), request.name().trim(), request.capacity());
    }

    public List<Form> forms(long org, long id, Authentication auth) {
        access.require(org, auth);
        evaluations.require(org, id);
        return evaluations.forms(id);
    }

    @Transactional
    public long createForm(long org, long id, CreateForm request, Authentication auth) {
        access.require(org, auth, "ADMINISTRADOR", "OPERADOR");
        publications.assertMutable(org, id);
        evaluations.require(org, id);
        return evaluations.createForm(id, request.code().trim(), request.questions(), request.options());
    }

    @Transactional
    public long saveKey(long org, long id, long formId, SaveKey request, Authentication auth) throws JsonProcessingException {
        long userId = access.require(org, auth, "ADMINISTRADOR", "OPERADOR").id();
        publications.assertMutable(org, id);
        evaluations.require(org, id);
        Form form = evaluations.form(id, formId).orElseThrow(() -> ApiException.notFound("Tema"));
        if (request.answers().size() != form.questions()) throw ApiException.badRequest("La clave debe tener una respuesta por pregunta");
        List<String> answers = request.answers().stream().map(answer -> answer.trim().toUpperCase()).toList();
        for (String answer : answers) {
            if (!answer.isEmpty() && (answer.length() != 1 || answer.charAt(0) < 'A' || answer.charAt(0) >= 'A' + form.options()))
                throw ApiException.badRequest("La clave contiene una opción inválida");
        }
        long keyId = evaluations.createKey(formId, json.writeValueAsString(answers), userId);
        audit.record(org, id, userId, "CLAVE_CREADA", "CLAVE", keyId, "{\"temaId\":" + formId + "}");
        return keyId;
    }

    public AnswerKey key(long org, long id, long formId, Authentication auth) {
        access.require(org, auth);
        evaluations.require(org, id);
        evaluations.form(id, formId).orElseThrow(() -> ApiException.notFound("Tema"));
        return evaluations.latestKey(formId).orElseThrow(() -> ApiException.notFound("Clave"));
    }

    @Transactional
    public long saveRule(long org, long id, SaveRule request, Authentication auth) {
        long userId = access.require(org, auth, "ADMINISTRADOR", "OPERADOR").id();
        publications.assertMutable(org, id);
        evaluations.require(org, id);
        long ruleId = evaluations.createRule(id, request.correct(), request.incorrect(), request.blank(), request.passingScore(), userId);
        audit.record(org, id, userId, "REGLA_CREADA", "REGLA", ruleId, "{}");
        return ruleId;
    }

    public Rule rule(long org, long id, Authentication auth) {
        access.require(org, auth);
        evaluations.require(org, id);
        return evaluations.latestRule(id).orElseThrow(() -> ApiException.notFound("Regla de calificación"));
    }

    public List<Participant> participants(long org, Authentication auth) {
        access.require(org, auth);
        return evaluations.participants(org);
    }

    public PageResult<Participant> participantsPage(long org, String query, String document,
                                                    int page, int size, Authentication auth) {
        return participantsPage(org,query,document,page,size,"",auth);
    }
    public PageResult<Participant> participantsPage(long org,String query,String document,int page,int size,String profile,Authentication auth) {
        access.require(org, auth);
        new PageRequest(page, size);
        if (!List.of("", "CON_DOCUMENTO", "SIN_DOCUMENTO").contains(document))
            throw ApiException.badRequest("Filtro de documento inválido");
        if (!List.of("","AUTOMATICOS","COMPLETOS","SIN_CORREO").contains(profile)) throw ApiException.badRequest("Filtro de datos inválido");
        return evaluations.participantsPage(org, query, document, page, size,profile);
    }

    @Transactional
    public void updateParticipant(long org, long participantId, UpdateParticipant request, Authentication auth) {
        access.require(org, auth, "ADMINISTRADOR", "OPERADOR");
        if (evaluations.updateParticipant(org, participantId, request.names().trim(),
                request.surnames(), request.document(),request.email()) == 0) throw ApiException.notFound("Participante");
    }

    @Transactional
    public long createParticipant(long org, CreateParticipant request, Authentication auth) {
        access.require(org, auth, "ADMINISTRADOR", "OPERADOR");
        return evaluations.registerParticipant(org, request.code().trim(), request.names().trim(),
                request.surnames(), request.document(),request.email()).orElseThrow(() ->
                ApiException.conflict("Ya existe un participante registrado con ese código")).id();
    }

    @Transactional
    public long register(long org, long id, RegisterParticipant request, Authentication auth) {
        access.require(org, auth, "ADMINISTRADOR", "OPERADOR");
        publications.assertMutable(org, id);
        evaluations.require(org, id);
        if (evaluations.isRegistered(org,id,request.participantId())) throw ApiException.conflict("El participante ya está inscrito en esta evaluación");
        return evaluations.register(org, id, request.participantId(), request.groupId());
    }

    public RegistrationStatus registrationStatus(long org, long evaluation, long participant, Authentication auth) {
        access.require(org, auth);
        evaluations.require(org, evaluation);
        if (!evaluations.participantExists(org, participant)) throw ApiException.notFound("Participante");
        return new RegistrationStatus(evaluations.isRegistered(org, evaluation, participant));
    }

    public PageResult<Registration> registrationsPage(long org, long id, String query, Long groupId,
                                                       int page, int size, Authentication auth) {
        access.require(org, auth);
        evaluations.require(org, id);
        new PageRequest(page, size);
        return evaluations.registrationsPage(org, id, query, groupId, page, size);
    }

    @Transactional
    public void updateRegistration(long org, long id, long registrationId,
                                   UpdateRegistration request, Authentication auth) {
        access.require(org, auth, "ADMINISTRADOR", "OPERADOR");
        publications.assertMutable(org, id);
        evaluations.require(org, id);
        if (evaluations.updateRegistrationGroup(org, id, registrationId, request.groupId()) == 0)
            throw ApiException.notFound("Inscripción");
        exams.recalculateRanking(id);
    }
}
