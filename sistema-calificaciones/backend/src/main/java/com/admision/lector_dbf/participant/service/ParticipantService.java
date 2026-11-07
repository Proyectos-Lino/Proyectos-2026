package com.admision.lector_dbf.participant.service;

import com.admision.lector_dbf.audit.repository.AuditRepository;
import com.admision.lector_dbf.evaluation.repository.EvaluationRepository;
import com.admision.lector_dbf.exam.repository.ExamRepository;
import com.admision.lector_dbf.participant.model.ParticipantModels.*;
import com.admision.lector_dbf.participant.model.ParticipantRequests.SaveEnrollment;
import com.admision.lector_dbf.participant.repository.ParticipantRepository;
import com.admision.lector_dbf.publication.repository.PublicationRepository;
import com.admision.lector_dbf.security.OrganizationAccess;
import com.admision.lector_dbf.shared.*;
import java.util.List;
import java.util.Map;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ParticipantService {
    private final ParticipantRepository people;
    private final EnrollmentPlanner planner;
    private final EvaluationRepository evaluations;
    private final PublicationRepository publications;
    private final ExamRepository exams;
    private final AuditRepository audit;
    private final OrganizationAccess access;

    public ParticipantService(ParticipantRepository people, EnrollmentPlanner planner, EvaluationRepository evaluations,
            PublicationRepository publications, ExamRepository exams, AuditRepository audit, OrganizationAccess access) {
        this.people = people; this.planner = planner; this.evaluations = evaluations;
        this.publications = publications; this.exams = exams; this.audit = audit; this.access = access;
    }

    public PageResult<Enrollment> page(long org, long evaluation, String query, Long groupId,
            String profile, String document, int page, int size, Authentication auth) {
        access.require(org, auth); evaluations.require(org, evaluation); new PageRequest(page, size);
        if (!List.of("", "AUTOMATICOS", "COMPLETOS", "SIN_CORREO").contains(profile)
                || !List.of("", "CON_DOCUMENTO", "SIN_DOCUMENTO").contains(document)) throw ApiException.badRequest("Filtro inválido");
        if (groupId != null && groupId != 0 && evaluations.groups(evaluation).stream().noneMatch(group -> group.id() == groupId))
            throw ApiException.badRequest("El grupo no pertenece a la evaluación");
        return people.page(org, evaluation, query, groupId, profile, document, page, size);
    }

    public Summary summary(long org, long evaluation, Authentication auth) {
        access.require(org, auth); evaluations.require(org, evaluation);
        return people.summary(evaluation);
    }

    public PageResult<Person> people(long org, String query, int page, int size, Authentication auth) {
        access.require(org, auth); new PageRequest(page, size);
        return people.people(org, query, page, size);
    }

    @Transactional
    public Enrollment create(long org, long evaluation, SaveEnrollment request, Authentication auth) {
        long actor = lock(org, evaluation, auth);
        var input = planner.normalize(request, evaluations.groups(evaluation));
        var existing = people.byCode(org, evaluation, input.code()).orElse(null);
        var plan = planner.plan(org, evaluation, input, existing, false);
        long id = planner.apply(org, evaluation, plan);
        exams.recalculateRanking(evaluation);
        audit.record(org, evaluation, actor, "PARTICIPANTE_INSCRITO", "INSCRIPCION", id, Map.of("codigo", input.code()));
        return people.require(org, evaluation, id);
    }

    @Transactional
    public Enrollment update(long org, long evaluation, long id, SaveEnrollment request, Authentication auth) {
        long actor = lock(org, evaluation, auth);
        var existing = people.require(org, evaluation, id);
        var input = planner.normalize(request, evaluations.groups(evaluation));
        if (existing.hasExam() && !existing.code().equals(input.code()))
            throw ApiException.conflict("El código ya tiene un examen asociado y no puede cambiarse");
        var collision = people.byCode(org, evaluation, input.code());
        if (collision.isPresent() && collision.get().id() != id) throw ApiException.conflict("Ya existe otra inscripción con ese código");
        if (input.personId() != null && input.personId() != existing.personId() && !existing.automatic())
            throw ApiException.conflict("No se puede reemplazar a una persona con datos registrados");
        if (existing.automatic()) {
            planner.apply(org, evaluation, planner.plan(org, evaluation, input, existing, true));
        } else {
            var matches = input.document().isBlank() ? List.<Person>of() : people.byDocument(org, input.documentType(), EnrollmentPlanner.documentKey(input.document()));
            if (matches.stream().anyMatch(person -> person.id() != existing.personId()))
                throw ApiException.conflict("El documento está registrado en otra persona. Revisa su identidad antes de cambiarlo");
            people.updatePerson(org, existing.personId(), input.names(), input.surnames(), input.documentType(), input.document(), input.email(), false);
            people.updateEnrollment(org, evaluation, id, existing.personId(), input.code(), input.groupId());
        }
        exams.recalculateRanking(evaluation);
        audit.record(org, evaluation, actor, "PARTICIPANTE_ACTUALIZADO", "INSCRIPCION", id,
                Map.of("codigo", input.code(), "grupoAnterior", existing.groupId() == null ? "" : existing.groupId(),
                        "grupoNuevo", input.groupId() == null ? "" : input.groupId()));
        return people.require(org, evaluation, id);
    }

    public long lock(long org, long evaluation, Authentication auth) {
        long actor = access.require(org, auth, "ADMINISTRADOR", "OPERADOR").id();
        people.lockOrganization(org);
        publications.assertMutable(org, evaluation);
        evaluations.require(org, evaluation);
        return actor;
    }
}
