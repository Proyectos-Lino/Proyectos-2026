package com.odontocare.treatments.service;

import com.odontocare.audit.service.AuditService;
import com.odontocare.catalog.repository.DentalServiceRepository;
import com.odontocare.clinical.service.ClinicalAccess;
import com.odontocare.dentists.repository.DentistRepository;
import com.odontocare.finance.repository.ChargeEntryRepository;
import com.odontocare.finance.service.ChargeLedgerService;
import com.odontocare.installation.repository.InstallationProfileRepository;
import com.odontocare.patients.repository.PatientRepository;
import com.odontocare.shared.pagination.*;
import com.odontocare.shared.web.ApiException;
import com.odontocare.treatments.dto.TreatmentContracts.*;
import com.odontocare.treatments.model.*;
import com.odontocare.treatments.repository.*;
import java.math.BigDecimal;
import java.time.Clock;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TreatmentPlanService {
  private final TreatmentPlanRepository plans;
  private final PlanItemRepository items;
  private final PlanOperationRepository operations;
  private final PlanSessionRepository sessions;
  private final ChargeEntryRepository charges;
  private final ChargeLedgerService ledger;
  private final PatientRepository patients;
  private final DentistRepository dentists;
  private final DentalServiceRepository services;
  private final InstallationProfileRepository profiles;
  private final ClinicalAccess access;
  private final AuditService audit;
  private final Clock clock;
  private final TreatmentPlanningRepository planning;
  private final SessionDates dates;
  private final PlanAgreementService agreements;
  private final com.odontocare.appointments.service.AppointmentService appointments;
  private final TreatmentProformaService proformas;

  public TreatmentPlanService(
      TreatmentPlanRepository plans,
      PlanItemRepository items,
      PlanOperationRepository operations,
      PlanSessionRepository sessions,
      ChargeEntryRepository charges,
      ChargeLedgerService ledger,
      PatientRepository patients,
      DentistRepository dentists,
      DentalServiceRepository services,
      InstallationProfileRepository profiles,
      ClinicalAccess access,
      AuditService audit,
      Clock clock,
      TreatmentPlanningRepository planning,
      SessionDates dates,
      PlanAgreementService agreements,
      com.odontocare.appointments.service.AppointmentService appointments,
      TreatmentProformaService proformas) {
    this.plans = plans;
    this.items = items;
    this.operations = operations;
    this.sessions = sessions;
    this.charges = charges;
    this.ledger = ledger;
    this.patients = patients;
    this.dentists = dentists;
    this.services = services;
    this.profiles = profiles;
    this.access = access;
    this.audit = audit;
    this.clock = clock;
    this.planning = planning;
    this.dates = dates;
    this.agreements = agreements;
    this.appointments = appointments;
    this.proformas = proformas;
  }

  @Transactional
  public PageResponse<PlanResponse> list(
      UUID patientId, UUID dentistId, String status, PageQuery query) {
    if (status != null
        && !Set.of("DRAFT", "PROPOSED", "ACCEPTED", "IN_PROGRESS", "FINISHED", "CANCELLED")
            .contains(status)) throw ApiException.badRequest("Estado de plan no válido.");
    var page =
        plans.findAll(
            SearchSpecifications.<TreatmentPlan>text(
                    query.getSearch(), "code", "title", "patientName", "dentistName")
                .and(SearchSpecifications.equal("patientId", patientId))
                .and(SearchSpecifications.equal("dentistId", dentistId))
                .and(SearchSpecifications.equal("status", status)),
            query.pageable(Map.of("name", "title", "createdAt", "createdAt", "code", "code")));
    audit.record("PLANS_READ", "PLAN", patientId, "Consultó presupuestos y planes");
    return PageResponse.of(page.map(this::response));
  }

  @Transactional
  public PlanResponse get(UUID id) {
    var plan = required(id);
    audit.record("PLAN_READ", "PLAN", id, "Consultó plan de tratamiento");
    return response(plan);
  }

  @Transactional
  public PlanResponse save(UUID id, PlanRequest request) {
    if (id != null
        && !plans.patientId(id).orElseThrow(ApiException::notFound).equals(request.patientId()))
      throw ApiException.badRequest("No se puede cambiar el paciente del plan.");
    var patient = patients.lockById(request.patientId()).orElseThrow(ApiException::notFound);
    if (!patient.getActive()) throw ApiException.badRequest("El paciente está inactivo.");
    String action = id == null ? "CREATED" : "UPDATED",
        hash = ledger.fingerprint(Arrays.asList(action, id, request));
    var prior = operations.findByRequestKey(request.requestKey());
    if (prior.isPresent()) return replay(prior.get(), hash);
    var dentist = dentists.findById(request.dentistId()).orElseThrow(ApiException::notFound);
    if (!dentist.getActive() || !dentist.getUser().getActive())
      throw ApiException.badRequest("Selecciona un odontólogo activo.");
    var plan =
        id == null ? new TreatmentPlan() : plans.lockById(id).orElseThrow(ApiException::notFound);
    if (id != null) {
      if (!plan.getPatientId().equals(request.patientId()))
        throw ApiException.badRequest("No se cambia el paciente del plan.");
      plan.checkVersion(request.version());
      if (!Set.of("DRAFT", "PROPOSED").contains(plan.getStatus()))
        throw ApiException.conflict(
            "El acuerdo aceptado requiere adicionales o ajustes explícitos.");
      items.deleteByPlanId(id);
      items.flush();
      plan.setStatus("DRAFT");
    } else {
      var profile = profiles.lockInstallation().orElseThrow();
      plan.setCode(
          profile.getBudgetPrefix() + "-" + String.format("%06d", profile.getBudgetNextNumber()));
      profile.setBudgetNextNumber(Math.addExact(profile.getBudgetNextNumber(), 1));
      plan.setCurrency(profile.getCurrency());
      plan.setStatus("DRAFT");
    }
    plan.setPatientId(patient.getId());
    plan.setPatientName(patient.getFullName());
    plan.setDentistId(dentist.getId());
    plan.setDentistName(dentist.getFullName());
    plan.setTitle(request.title().strip());
    plan.setConditions(request.conditions().strip());
    plan.advanceRevision();
    plans.saveAndFlush(plan);
    int position = 1;
    for (var item : request.items()) addItem(plan, item, position++);
    items.flush();
    planning.agreement(
        plan.getId(), agreements.validate(request.paymentAgreement(), items.total(plan.getId())));
    operation(
        plan,
        request.requestKey(),
        hash,
        action,
        "Registro de presupuesto",
        "Guardó tratamientos e importes propuestos");
    return response(plan);
  }

  @Transactional
  public PlanResponse action(UUID id, String action, ActionRequest request) {
    UUID patientId = plans.patientId(id).orElseThrow(ApiException::notFound);
    patients.lockById(patientId).orElseThrow();
    var plan = plans.lockById(id).orElseThrow();
    String hash = ledger.fingerprint(List.of(id, action, request));
    var prior = operations.findByRequestKey(request.requestKey());
    if (prior.isPresent()) return replay(prior.get(), hash);
    if (action.equals("accept")
        && plan.getAcceptedAt() != null
        && Set.of("ACCEPTED", "IN_PROGRESS", "FINISHED").contains(plan.getStatus()))
      return response(plan);
    plan.checkVersion(request.version());
    switch (action) {
      case "propose" -> {
        if (!plan.getStatus().equals("DRAFT"))
          throw ApiException.conflict("Solo se propone un borrador.");
        plan.setStatus("PROPOSED");
      }
      case "accept" -> {
        if (!plan.getStatus().equals("PROPOSED"))
          throw ApiException.conflict("Presenta el presupuesto antes de aceptarlo.");
        if (request.acceptedBy() == null || request.acceptedBy().isBlank())
          throw ApiException.badRequest("Registra quién acepta expresamente el plan.");
        var patient = patients.findById(patientId).orElseThrow();
        if (!patient.getActive())
          throw ApiException.badRequest("Activa la ficha del paciente antes de aceptar.");
        plan.setStatus("ACCEPTED");
        plan.setAcceptedAt(clock.instant());
        plan.setAcceptedBy(request.acceptedBy().strip());
        plan.setAcceptedTotal(items.total(id));
        for (var item : items.findByPlanIdOrderByPosition(id))
          issue(plan, item, "Aceptación explícita del plan");
        charges.flush();
        agreements.apply(plan, request.initialCollection());
        for (var item : items.findByPlanIdOrderByPosition(id)) schedule(plan, item);
      }
      case "finish" -> {
        if (!Set.of("ACCEPTED", "IN_PROGRESS").contains(plan.getStatus())
            || sessions.total(id) != items.sessions(id))
          throw ApiException.conflict("Completa todas las sesiones para finalizar el plan.");
        plan.setStatus("FINISHED");
      }
      case "cancel" -> {
        if (Set.of("FINISHED", "CANCELLED").contains(plan.getStatus()))
          throw ApiException.conflict("El plan ya está cerrado.");
        if (plan.getAcceptedAt() != null
            && Boolean.TRUE.equals(request.releaseUnperformed())
            && !access.actor().getPermissions().contains("FINANCES_ADJUST"))
          throw ApiException.forbidden();
        if (plan.getAcceptedAt() != null && Boolean.TRUE.equals(request.releaseUnperformed()))
          for (var item : items.findByPlanIdOrderByPosition(id))
            ledger.releaseUnperformed(item, request.requestKey(), request.reason());
        plan.setStatus("CANCELLED");
        plans.saveAndFlush(plan);
        var future = planning.futureAppointments(id, clock.instant());
        if (!future.isEmpty() && !access.actor().getPermissions().contains("APPOINTMENTS_WRITE"))
          throw ApiException.forbidden();
        for (var appointment : future) {
          var current = appointmentsQueryVersion(appointment);
          appointments.changeStatus(
              appointment,
              new com.odontocare.appointments.dto.StatusRequest(
                  current,
                  com.odontocare.appointments.model.AppointmentStatus.CANCELLED,
                  "Cancelación del plan: " + request.reason()));
        }
        planning.cancelPending(id);
      }
      default -> throw ApiException.badRequest("Operación de plan no válida.");
    }
    plan.advanceRevision();
    plans.saveAndFlush(plan);
    operation(
        plan,
        request.requestKey(),
        hash,
        action.toUpperCase(Locale.ROOT),
        request.reason(),
        "Estado "
            + plan.getStatus()
            + "; deuda vigente "
            + charges.planDebt(id)
            + " "
            + plan.getCurrency());
    if (action.equals("accept") || action.equals("propose")) proformas.emit(id);
    return response(plan);
  }

  private long appointmentsQueryVersion(UUID id) {
    return planning.appointmentVersion(id);
  }

  @Transactional
  public PlanResponse additional(UUID id, AdditionalRequest request) {
    patients.lockById(plans.patientId(id).orElseThrow(ApiException::notFound)).orElseThrow();
    var plan = plans.lockById(id).orElseThrow();
    String hash = ledger.fingerprint(List.of(id, "additional", request));
    var prior = operations.findByRequestKey(request.requestKey());
    if (prior.isPresent()) return replay(prior.get(), hash);
    plan.checkVersion(request.version());
    if (!Set.of("ACCEPTED", "IN_PROGRESS").contains(plan.getStatus()))
      throw ApiException.conflict("El adicional requiere un plan aceptado y vigente.");
    int position = items.lastPosition(id) + 1;
    if (position > 100)
      throw ApiException.badRequest(
          "El plan admite hasta 100 tratamientos, incluidos adicionales.");
    var item = addItem(plan, request.item(), position);
    items.flush();
    issue(plan, item, request.reason());
    schedule(plan, item);
    plan.advanceRevision();
    plans.saveAndFlush(plan);
    operation(
        plan,
        request.requestKey(),
        hash,
        "ADDITIONAL",
        request.reason(),
        "Agregó tratamiento adicional con cargo propio");
    return response(plan);
  }

  @Transactional
  public PageResponse<ItemResponse> listItems(
      UUID planId, UUID patientId, UUID dentistId, Boolean available, PageQuery query) {
    var spec =
        SearchSpecifications.<PlanItem>text(query.getSearch(), "description", "serviceName")
            .and(SearchSpecifications.equal("planId", planId));
    if (patientId != null || dentistId != null || Boolean.TRUE.equals(available))
      spec =
          spec.and(
              (root, cq, cb) -> {
                var sub = cq.subquery(UUID.class);
                var plan = sub.from(TreatmentPlan.class);
                var predicates = new ArrayList<jakarta.persistence.criteria.Predicate>();
                predicates.add(cb.equal(plan.get("id"), root.get("planId")));
                if (patientId != null) predicates.add(cb.equal(plan.get("patientId"), patientId));
                if (dentistId != null) predicates.add(cb.equal(plan.get("dentistId"), dentistId));
                if (Boolean.TRUE.equals(available))
                  predicates.add(plan.get("status").in("ACCEPTED", "IN_PROGRESS"));
                sub.select(plan.get("id"))
                    .where(predicates.toArray(jakarta.persistence.criteria.Predicate[]::new));
                return cb.exists(sub);
              });
    if (Boolean.TRUE.equals(available))
      spec =
          spec.and(
              (root, cq, cb) -> {
                var sum = cq.subquery(Long.class);
                var s = sum.from(PlanSession.class);
                sum.select(cb.coalesce(cb.sum(s.<Long>get("sessions")), 0L))
                    .where(cb.equal(s.get("itemId"), root.get("id")));
                return cb.lessThan(sum, root.get("sessions"));
              });
    var page =
        items.findAll(
            spec,
            query.pageable(
                Map.of("name", "description", "position", "position", "unitPrice", "unitPrice")));
    return PageResponse.of(page.map(this::itemResponse));
  }

  @Transactional
  public PageResponse<SessionResponse> completedSessions(UUID id, UUID itemId, PageQuery query) {
    required(id);
    var page =
        sessions.findAll(
            SearchSpecifications.<PlanSession>text(query.getSearch(), "actorName")
                .and(SearchSpecifications.equal("planId", id))
                .and(SearchSpecifications.equal("itemId", itemId)),
            query.pageable(Map.of("name", "createdAt", "createdAt", "createdAt")));
    audit.record("PLAN_SESSIONS_READ", "PLAN", id, "Consultó sesiones realizadas");
    return PageResponse.of(
        page.map(
            s ->
                new SessionResponse(
                    s.getId(),
                    s.getItemId(),
                    s.getEncounterId(),
                    items.findById(s.getItemId()).orElseThrow().getDescription(),
                    s.getSessions(),
                    s.getActorName(),
                    s.getCreatedAt())));
  }

  @Transactional
  public ItemResponse getItem(UUID id) {
    return itemResponse(items.findById(id).orElseThrow(ApiException::notFound));
  }

  @Transactional
  public PageResponse<OperationResponse> history(UUID id, PageQuery query) {
    required(id);
    var page =
        operations.findAll(
            SearchSpecifications.<PlanOperation>text(
                    query.getSearch(), "reason", "actorName", "action", "summary")
                .and(SearchSpecifications.equal("planId", id)),
            query.pageable(Map.of("name", "createdAt", "createdAt", "createdAt")));
    return PageResponse.of(
        page.map(
            o ->
                new OperationResponse(
                    o.getId(),
                    o.getPlanId(),
                    o.getAction(),
                    o.getReason(),
                    o.getActorName(),
                    o.getSummary(),
                    o.getCreatedAt())));
  }

  private PlanItem addItem(TreatmentPlan plan, ItemRequest request, int position) {
    ClinicalAccess.requireTooth(request.tooth());
    var service =
        request.serviceId() == null
            ? null
            : services.findById(request.serviceId()).orElseThrow(ApiException::notFound);
    if (service != null && !service.getActive())
      throw ApiException.badRequest("El servicio está inactivo.");
    var item = new PlanItem();
    item.setPlanId(plan.getId());
    item.setServiceId(request.serviceId());
    item.setServiceName(service == null ? "" : service.getName());
    item.setDescription(request.description().strip());
    item.setTooth(request.tooth());
    item.setQuantity(request.quantity());
    item.setSessions(request.sessions());
    item.setUnitPrice(request.unitPrice().setScale(2));
    item.setPosition(position);
    int duration =
        request.durationMinutes() != null
            ? request.durationMinutes()
            : service == null ? 30 : service.getDurationMinutes();
    if (duration < 1 || duration > 1440)
      throw ApiException.badRequest("Indica una duración positiva para las sesiones.");
    dates.calculate(request.scheduling(), request.sessions());
    item.setDurationMinutes(duration);
    item.setScheduling(planning.encode(request.scheduling()));
    items.saveAndFlush(item);
    return item;
  }

  private void schedule(TreatmentPlan plan, PlanItem item) {
    var calculated = dates.calculate(planning.scheduling(item.getId()), item.getSessions());
    for (int index = 0; index < calculated.size(); index++)
      planning.create(
          plan.getId(),
          item.getId(),
          index + 1,
          calculated.get(index),
          planning.duration(item.getId()));
  }

  private void issue(TreatmentPlan plan, PlanItem item, String reason) {
    ledger.issue(
        plan.getPatientId(),
        plan.getId(),
        item.getId(),
        null,
        "plan:" + item.getId(),
        item.getDescription(),
        plan.getCurrency(),
        item.getUnitPrice(),
        item.getQuantity(),
        reason);
  }

  private void operation(
      TreatmentPlan plan, UUID key, String hash, String action, String reason, String summary) {
    var op = new PlanOperation();
    op.setPlanId(plan.getId());
    op.setRequestKey(key);
    op.setFingerprint(hash);
    op.setAction(action);
    op.setReason(reason.strip());
    op.setSummary(summary);
    op.setActorName(access.actor().getDisplayName());
    operations.saveAndFlush(op);
    audit.record("PLAN_" + action, "PLAN", plan.getId(), summary);
  }

  private PlanResponse replay(PlanOperation operation, String hash) {
    if (!operation.getFingerprint().equals(hash))
      throw ApiException.conflict("La clave de operación ya se utilizó con otros datos.");
    return response(required(operation.getPlanId()));
  }

  private TreatmentPlan required(UUID id) {
    return plans.findById(id).orElseThrow(ApiException::notFound);
  }

  private PlanResponse response(TreatmentPlan p) {
    return new PlanResponse(
        p.getId(),
        p.getPatientId(),
        p.getDentistId(),
        p.getDentistName(),
        p.getPatientName(),
        p.getCode(),
        p.getTitle(),
        p.getConditions(),
        p.getCurrency(),
        p.getStatus(),
        p.getAcceptedBy(),
        p.getAcceptedAt(),
        p.getAcceptedTotal() == null ? items.total(p.getId()) : p.getAcceptedTotal(),
        charges.planDebt(p.getId()),
        sessions.total(p.getId()),
        items.sessions(p.getId()),
        p.getVersion(),
        planning.agreement(p.getId()),
        planning.payment(p.getId()));
  }

  private ItemResponse itemResponse(PlanItem i) {
    return new ItemResponse(
        i.getId(),
        i.getPlanId(),
        i.getServiceId(),
        i.getServiceName(),
        i.getDescription(),
        i.getTooth(),
        i.getQuantity(),
        i.getSessions(),
        i.getUnitPrice(),
        i.getUnitPrice().multiply(BigDecimal.valueOf(i.getQuantity())),
        sessions.completed(i.getId()),
        charges.itemDebt(i.getId()),
        required(i.getPlanId()).getCode() + " · " + i.getDescription(),
        planning.duration(i.getId()),
        planning.scheduling(i.getId()));
  }
}
