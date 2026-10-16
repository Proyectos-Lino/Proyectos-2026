package com.odontocare.clinical.service;

import com.odontocare.appointments.model.AppointmentStatus;
import com.odontocare.appointments.repository.AppointmentRepository;
import com.odontocare.appointments.service.AppointmentService;
import com.odontocare.audit.service.AuditService;
import com.odontocare.catalog.repository.DentalServiceRepository;
import com.odontocare.clinical.dto.ClinicalContracts.*;
import com.odontocare.clinical.model.*;
import com.odontocare.clinical.repository.*;
import com.odontocare.patients.repository.PatientRepository;
import com.odontocare.shared.pagination.*;
import com.odontocare.shared.web.ApiException;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

@Service
public class EncounterService {
  private final EncounterRepository encounters;
  private final EncounterRevisionRepository revisions;
  private final PatientRepository patients;
  private final AppointmentRepository appointments;
  private final DentalServiceRepository services;
  private final ClinicalAccess access;
  private final AuditService audit;
  private final ObjectMapper mapper;
  private final AppointmentService booking;
  private final com.odontocare.finance.service.ClinicalBillingService billing;

  public EncounterService(
      EncounterRepository encounters,
      EncounterRevisionRepository revisions,
      PatientRepository patients,
      AppointmentRepository appointments,
      DentalServiceRepository services,
      ClinicalAccess access,
      AuditService audit,
      ObjectMapper mapper,
      AppointmentService booking,
      com.odontocare.finance.service.ClinicalBillingService billing) {
    this.encounters = encounters;
    this.revisions = revisions;
    this.patients = patients;
    this.appointments = appointments;
    this.services = services;
    this.access = access;
    this.audit = audit;
    this.mapper = mapper;
    this.booking = booking;
    this.billing = billing;
  }

  @Transactional
  public PageResponse<EncounterResponse> list(
      UUID patientId, String status, UUID dentistId, PageQuery query) {
    if (status != null && !Set.of("DRAFT", "FINAL").contains(status))
      throw ApiException.badRequest("Estado de atención no válido.");
    var page =
        encounters.findAll(
            SearchSpecifications.<Encounter>text(query.getSearch(), "reason")
                .and(SearchSpecifications.equal("patientId", patientId))
                .and(SearchSpecifications.equal("status", status))
                .and(SearchSpecifications.equal("dentistId", dentistId)),
            query.pageable(
                Map.of(
                    "name", "attendedOn", "attendedOn", "attendedOn", "createdAt", "createdAt")));
    audit.record("ENCOUNTER_READ", "ENCOUNTER", patientId, "Consultó listado de atenciones");
    return PageResponse.of(page.map(encounter -> response(encounter, false)));
  }

  @Transactional
  public EncounterResponse get(UUID id) {
    var encounter = required(id);
    audit.record("ENCOUNTER_READ", "ENCOUNTER", id, "Consultó una atención");
    return response(encounter, true);
  }

  @Transactional
  public EncounterResponse save(UUID id, EncounterRequest request) {
    var dentist = access.requireDentist(request.dentistId());
    access.requireDate(request.attendedOn());
    var patient = patients.findById(request.patientId()).orElseThrow(ApiException::notFound);
    if (!patient.getActive()) throw ApiException.badRequest("El paciente está inactivo.");
    var encounter = id == null ? new Encounter() : required(id);
    if (id != null) {
      encounter.checkVersion(request.version());
      if (!encounter.getStatus().equals("DRAFT"))
        throw ApiException.conflict(
            "La atención finalizada solo admite correcciones que conserven el original.");
      if (!encounter.getPatientId().equals(patient.getId()))
        throw ApiException.badRequest("No se puede cambiar el paciente de una atención.");
      access.requireDentist(encounter.getDentistId());
    }
    if (request.appointmentId() != null) {
      var appointment =
          appointments.findById(request.appointmentId()).orElseThrow(ApiException::notFound);
      if (!appointment.getPatient().getId().equals(patient.getId())
          || !appointment.getDentist().getId().equals(dentist.getId()))
        throw ApiException.badRequest(
            "La cita no corresponde al paciente y odontólogo seleccionados.");
      if (Set.of(AppointmentStatus.CANCELLED, AppointmentStatus.NO_SHOW)
          .contains(appointment.getStatus()))
        throw ApiException.badRequest("No se puede enlazar una cita cancelada o con ausencia.");
      if (!Objects.equals(encounter.getAppointmentId(), request.appointmentId())
          && encounters.existsByAppointmentId(request.appointmentId()))
        throw ApiException.conflict("La cita ya tiene una atención.");
    }
    validateProcedures(request.content());
    encounter.setPatientId(patient.getId());
    encounter.setDentistId(dentist.getId());
    encounter.setAppointmentId(request.appointmentId());
    encounter.setAttendedOn(request.attendedOn());
    String reason = request.reason().strip();
    if (reason.isEmpty()) {
      reason =
          request.content().procedures().stream()
              .map(Procedure::description)
              .collect(java.util.stream.Collectors.joining(", "));
      if (reason.isEmpty()) reason = "Atención odontológica";
    }
    encounter.setReason(reason.substring(0, Math.min(500, reason.length())));
    encounter.setDraft(mapper.writeValueAsString(request.content()));
    encounters.saveAndFlush(encounter);
    audit.record(
        id == null ? "ENCOUNTER_CREATED" : "ENCOUNTER_DRAFT_UPDATED",
        "ENCOUNTER",
        encounter.getId(),
        "Guardó borrador clínico");
    return response(encounter, true);
  }

  @Transactional
  public EncounterResponse finish(UUID id, VersionRequest request) {
    patients.lockById(encounters.patientId(id).orElseThrow(ApiException::notFound)).orElseThrow();
    var encounter = required(id);
    access.requireDentist(encounter.getDentistId());
    if (encounter.getStatus().equals("FINAL")) return response(encounter, true);
    encounter.checkVersion(request.version());
    access.requireDentist(encounter.getDentistId());
    if (!encounter.getStatus().equals("DRAFT"))
      throw ApiException.conflict("La atención ya está finalizada.");
    var content = mapper.readValue(encounter.getDraft(), EncounterContent.class);
    if (encounter.getAppointmentId() != null) {
      var appointment = appointments.findById(encounter.getAppointmentId()).orElseThrow();
      if (appointment.getPlanItemId() != null
          && content.procedures().stream()
                  .filter(
                      p -> appointment.getPlanItemId().equals(p.planItemId()) && p.quantity() == 1)
                  .count()
              != 1)
        throw ApiException.badRequest(
            "Esta cita corresponde a una sesión de tratamiento. Conserva su procedimiento vinculado"
                + " para finalizar sin generar otra deuda.");
      booking.completeClinical(encounter.getAppointmentId());
    }
    billing.finalizeProcedures(encounter, content.procedures());
    append(encounter, content, "Finalización inicial");
    encounter.setStatus("FINAL");
    encounters.saveAndFlush(encounter);
    audit.record("ENCOUNTER_FINALIZED", "ENCOUNTER", id, "Finalizó atención clínica");
    return response(encounter, true);
  }

  @Transactional
  public EncounterResponse correct(UUID id, CorrectionRequest request) {
    var encounter = required(id);
    encounter.checkVersion(request.version());
    access.requireDentist(encounter.getDentistId());
    if (!encounter.getStatus().equals("FINAL"))
      throw ApiException.conflict("Solo se corrigen atenciones finalizadas.");
    validateProcedures(request.content());
    append(encounter, request.content(), request.correctionReason().strip());
    encounters.saveAndFlush(encounter);
    audit.record(
        "ENCOUNTER_CORRECTED",
        "ENCOUNTER",
        id,
        "Agregó versión clínica " + encounter.getRevision());
    return response(encounter, true);
  }

  @Transactional
  public PageResponse<RevisionResponse> history(UUID id, PageQuery query) {
    required(id);
    var page =
        revisions.findAll(
            SearchSpecifications.<EncounterRevision>text(
                    query.getSearch(), "correctionReason", "actorName")
                .and(SearchSpecifications.equal("encounterId", id)),
            query.pageable(Map.of("name", "number", "number", "number", "createdAt", "createdAt")));
    audit.record("ENCOUNTER_HISTORY_READ", "ENCOUNTER", id, "Consultó versiones de atención");
    return PageResponse.of(page.map(this::revisionResponse));
  }

  private void validateProcedures(EncounterContent content) {
    for (var procedure : content.procedures()) {
      ClinicalAccess.requireTooth(procedure.tooth());
      if (procedure.serviceId() != null && !services.existsById(procedure.serviceId()))
        throw ApiException.badRequest("El servicio del procedimiento no existe.");
    }
  }

  private void append(Encounter encounter, EncounterContent content, String reason) {
    validateProcedures(content);
    var patient = patients.findById(encounter.getPatientId()).orElseThrow();
    var dentist = access.requireDentist(encounter.getDentistId());
    var previous =
        encounter.getRevision() == 0
            ? null
            : revisions
                .findByEncounterIdAndNumber(encounter.getId(), encounter.getRevision())
                .orElseThrow();
    var revision = new EncounterRevision();
    revision.setEncounterId(encounter.getId());
    revision.setNumber(encounter.getRevision() + 1);
    revision.setPatientName(previous == null ? patient.getFullName() : previous.getPatientName());
    revision.setPatientCode(previous == null ? patient.getCode() : previous.getPatientCode());
    revision.setBirthDate(previous == null ? patient.getBirthDate() : previous.getBirthDate());
    revision.setPatientDocument(
        previous == null
            ? patient.getDocumentType() + " " + patient.getDocumentNumber()
            : previous.getPatientDocument());
    revision.setDentistName(previous == null ? dentist.getFullName() : previous.getDentistName());
    revision.setAttendedOn(encounter.getAttendedOn());
    revision.setReason(encounter.getReason());
    List<ProcedureSnapshot> snapshots = new ArrayList<>();
    for (var procedure : content.procedures()) {
      var service =
          procedure.serviceId() == null
              ? null
              : services.findById(procedure.serviceId()).orElseThrow();
      snapshots.add(
          new ProcedureSnapshot(
              procedure.serviceId(),
              procedure.description(),
              procedure.quantity(),
              procedure.tooth(),
              service == null ? "" : service.getName(),
              billing.unitPrice(procedure)));
    }
    revision.setPayload(mapper.writeValueAsString(new StoredEncounter(content, snapshots)));
    revision.setActorName(access.actor().getDisplayName());
    revision.setCorrectionReason(reason);
    revisions.saveAndFlush(revision);
    encounter.setRevision(revision.getNumber());
  }

  private Encounter required(UUID id) {
    return encounters.findById(id).orElseThrow(ApiException::notFound);
  }

  private EncounterResponse response(Encounter encounter, boolean detail) {
    var finalized =
        encounter.getRevision() == 0
            ? null
            : revisions
                .findByEncounterIdAndNumber(encounter.getId(), encounter.getRevision())
                .orElseThrow();
    EncounterContent content = null;
    if (detail)
      content =
          finalized == null
              ? mapper.readValue(encounter.getDraft(), EncounterContent.class)
              : mapper.readValue(finalized.getPayload(), StoredEncounter.class).content();
    return new EncounterResponse(
        encounter.getId(),
        encounter.getPatientId(),
        encounter.getDentistId(),
        finalized == null
            ? access.dentistName(encounter.getDentistId())
            : finalized.getDentistName(),
        encounter.getAppointmentId(),
        encounter.getAttendedOn(),
        encounter.getReason(),
        encounter.getStatus(),
        encounter.getRevision(),
        encounter.getVersion(),
        content);
  }

  private RevisionResponse revisionResponse(EncounterRevision revision) {
    var stored = mapper.readValue(revision.getPayload(), StoredEncounter.class);
    return new RevisionResponse(
        revision.getId(),
        revision.getEncounterId(),
        revision.getNumber(),
        revision.getPatientName(),
        revision.getPatientCode(),
        revision.getBirthDate(),
        revision.getPatientDocument(),
        revision.getDentistName(),
        revision.getAttendedOn(),
        revision.getReason(),
        stored.content(),
        stored.procedures(),
        revision.getActorName(),
        revision.getCorrectionReason(),
        revision.getCreatedAt());
  }
}
