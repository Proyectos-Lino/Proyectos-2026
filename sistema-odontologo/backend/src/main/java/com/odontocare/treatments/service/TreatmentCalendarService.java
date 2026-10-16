package com.odontocare.treatments.service;

import com.odontocare.appointments.dto.*;
import com.odontocare.appointments.service.AppointmentService;
import com.odontocare.audit.service.AuditService;
import com.odontocare.clinical.service.ClinicalAccess;
import com.odontocare.finance.service.ChargeLedgerService;
import com.odontocare.patients.repository.PatientRepository;
import com.odontocare.shared.pagination.*;
import com.odontocare.shared.web.ApiException;
import com.odontocare.treatments.dto.ScheduledSessionContracts.*;
import com.odontocare.treatments.model.PlanOperation;
import com.odontocare.treatments.repository.*;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TreatmentCalendarService {
  private final TreatmentPlanningRepository repository;
  private final PatientRepository patients;
  private final TreatmentPlanRepository plans;
  private final PlanOperationRepository history;
  private final AppointmentService appointments;
  private final ClinicalAccess access;
  private final ChargeLedgerService ledger;
  private final AuditService audit;

  public TreatmentCalendarService(
      TreatmentPlanningRepository repository,
      PatientRepository patients,
      TreatmentPlanRepository plans,
      PlanOperationRepository history,
      AppointmentService appointments,
      ClinicalAccess access,
      ChargeLedgerService ledger,
      AuditService audit) {
    this.repository = repository;
    this.patients = patients;
    this.plans = plans;
    this.history = history;
    this.appointments = appointments;
    this.access = access;
    this.ledger = ledger;
    this.audit = audit;
  }

  @Transactional(readOnly = true)
  public PageResponse<Session> list(
      UUID patient,
      UUID dentist,
      UUID plan,
      LocalDate from,
      LocalDate to,
      String status,
      PageQuery query) {
    return repository.list(patient, dentist, plan, from, to, status, query);
  }

  @Transactional(readOnly = true)
  public PageResponse<Session> list(
      UUID patient,
      UUID dentist,
      UUID plan,
      LocalDate from,
      LocalDate to,
      String status,
      boolean undated,
      PageQuery query) {
    return repository.list(patient, dentist, plan, from, to, status, undated, query);
  }

  @Transactional(readOnly = true)
  public Session get(UUID id) {
    return repository.session(id, false).orElseThrow(ApiException::notFound);
  }

  @Transactional(readOnly = true)
  public TreatmentPlanningRepository.CoordinationSummary coordination(UUID dentist) {
    return repository.coordination(dentist, access.today());
  }

  @Transactional
  public AppointmentResponse assign(UUID id, Assign request) {
    var session = get(id);
    return appointments.create(
        new AppointmentRequest(
            session.patientId(),
            session.dentistId(),
            session.serviceId(),
            session.description().substring(0, Math.min(160, session.description().length())),
            session.durationMinutes(),
            request.localStart(),
            "Sesión " + session.sessionNumber() + " del plan " + session.planCode(),
            request.requestKey(),
            false,
            session.itemId(),
            id,
            null,
            request.version()));
  }

  @Transactional
  public Session changeDate(UUID id, ChangeDate request) {
    var initial = get(id);
    patients.lockById(initial.patientId()).orElseThrow();
    var plan = plans.lockById(initial.planId()).orElseThrow();
    var session = repository.session(id, true).orElseThrow();
    if (!Set.of("ACCEPTED", "IN_PROGRESS").contains(plan.getStatus())
        || !session.status().equals("PENDING"))
      throw ApiException.conflict(
          "Solo se cambia la fecha prevista de una sesión pendiente; reprograma su cita si ya tiene"
              + " hora.");
    if (session.version() != request.version())
      throw ApiException.conflict("Actualiza la sesión; otra operación cambió su planificación.");
    repository.date(id, request.plannedOn());
    plan.advanceRevision();
    plans.saveAndFlush(plan);
    String summary =
        "Sesión "
            + session.sessionNumber()
            + ": "
            + Objects.toString(session.plannedOn(), "sin fecha")
            + " → "
            + request.plannedOn();
    var event = new PlanOperation();
    event.setPlanId(plan.getId());
    event.setRequestKey(UUID.randomUUID());
    event.setFingerprint(ledger.fingerprint(List.of(id, request)));
    event.setAction("SESSION_DATE");
    event.setReason(request.reason());
    event.setActorName(access.actor().getDisplayName());
    event.setSummary(summary);
    history.saveAndFlush(event);
    audit.record(
        "TREATMENT_SESSION_DATE", "TREATMENT_SESSION", id, summary + "; " + request.reason());
    return get(id);
  }
}
