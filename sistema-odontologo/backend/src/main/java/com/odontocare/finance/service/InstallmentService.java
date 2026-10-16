package com.odontocare.finance.service;

import com.odontocare.audit.service.AuditService;
import com.odontocare.clinical.service.ClinicalAccess;
import com.odontocare.finance.dto.PaymentContracts.*;
import com.odontocare.finance.model.*;
import com.odontocare.finance.repository.*;
import com.odontocare.patients.repository.PatientRepository;
import com.odontocare.shared.pagination.*;
import com.odontocare.shared.web.ApiException;
import java.math.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InstallmentService {
  private final InstallmentRepository installments;
  private final InstallmentScheduleRepository schedules;
  private final ChargeEntryRepository charges;
  private final MoneyApplicationRepository applications;
  private final PatientRepository patients;
  private final FinanceOperationService operations;
  private final ClinicalAccess access;
  private final AuditService audit;
  private final ChargeStateService states;

  public InstallmentService(
      InstallmentRepository installments,
      InstallmentScheduleRepository schedules,
      ChargeEntryRepository charges,
      MoneyApplicationRepository applications,
      PatientRepository patients,
      FinanceOperationService operations,
      ClinicalAccess access,
      AuditService audit,
      ChargeStateService states) {
    this.installments = installments;
    this.schedules = schedules;
    this.charges = charges;
    this.applications = applications;
    this.patients = patients;
    this.operations = operations;
    this.access = access;
    this.audit = audit;
    this.states = states;
  }

  @Transactional
  public UUID schedule(ScheduleRequest r) {
    var c = charges.findById(r.chargeId()).orElseThrow(ApiException::notFound);
    patients.lockById(c.getPatientId()).orElseThrow();
    var prior = operations.previous(r.requestKey(), "INSTALLMENTS", r);
    if (prior.isPresent()) return prior.get().getResultId();
    states.requireOpen(c.getId());
    if (c.getOriginalId() != null) throw ApiException.badRequest("Selecciona el cargo original.");
    var total =
        r.installments().stream().map(DueRequest::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
    var debt = charges.originDebt(c.getId());
    if (total.compareTo(debt) != 0)
      throw ApiException.badRequest(
          "Las cuotas deben distribuir el cargo neto completo, incluyendo lo ya pagado.");
    var sorted = r.installments().stream().sorted(Comparator.comparing(DueRequest::dueOn)).toList();
    schedules
        .findByChargeIdAndActiveTrue(c.getId())
        .ifPresent(
            old -> {
              old.setActive(false);
              schedules.saveAndFlush(old);
            });
    var s = new InstallmentSchedule();
    s.setPatientId(c.getPatientId());
    s.setChargeId(c.getId());
    s.setTotal(total);
    s.setActive(true);
    s.setReason(r.reason().strip());
    s.setActorName(access.actor().getDisplayName());
    schedules.saveAndFlush(s);
    int position = 0;
    for (var due : sorted) {
      var i = new Installment();
      i.setScheduleId(s.getId());
      i.setPosition(++position);
      i.setDueOn(due.dueOn());
      i.setAmount(due.amount());
      installments.save(i);
    }
    installments.flush();
    operations.record(r.requestKey(), "INSTALLMENTS", r, s.getId(), r.reason());
    audit.record(
        "INSTALLMENTS_SCHEDULED",
        "INSTALLMENT",
        s.getId(),
        "Distribuyó vencimientos sin generar cargos");
    return s.getId();
  }

  @Transactional
  public PageResponse<DueResponse> list(
      UUID patient,
      UUID charge,
      Boolean active,
      java.time.LocalDate from,
      java.time.LocalDate to,
      PageQuery query) {
    if (from != null && to != null && from.isAfter(to))
      throw ApiException.badRequest("Intervalo de fechas no válido.");
    var spec = SearchSpecifications.<Installment>text("");
    spec =
        spec.and(
            (r, c, b) -> {
              var sub = c.subquery(UUID.class);
              var s = sub.from(InstallmentSchedule.class);
              var conditions = new ArrayList<jakarta.persistence.criteria.Predicate>();
              conditions.add(b.equal(s.get("patientId"), patient));
              if (charge != null) conditions.add(b.equal(s.get("chargeId"), charge));
              if (active != null) conditions.add(b.equal(s.get("active"), active));
              if (!query.getSearch().isBlank())
                conditions.add(
                    b.like(
                        b.lower(s.get("reason")),
                        "%"
                            + query
                                .getSearch()
                                .toLowerCase(java.util.Locale.ROOT)
                                .replace("%", "\\%")
                                .replace("_", "\\_")
                            + "%",
                        (char) 92));
              sub.select(s.get("id"))
                  .where(conditions.toArray(jakarta.persistence.criteria.Predicate[]::new));
              return r.get("scheduleId").in(sub);
            });
    if (from != null) spec = spec.and((r, c, b) -> b.greaterThanOrEqualTo(r.get("dueOn"), from));
    if (to != null) spec = spec.and((r, c, b) -> b.lessThanOrEqualTo(r.get("dueOn"), to));
    return PageResponse.of(
        installments
            .findAll(
                spec, query.pageable(Map.of("name", "dueOn", "dueOn", "dueOn", "amount", "amount")))
            .map(this::response));
  }

  private DueResponse response(Installment i) {
    var s = schedules.findById(i.getScheduleId()).orElseThrow();
    var c = charges.findById(s.getChargeId()).orElseThrow();
    var preceding =
        installments.findByScheduleIdOrderByPosition(s.getId()).stream()
            .filter(row -> row.getPosition() < i.getPosition())
            .map(Installment::getAmount)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    var paid =
        applications
            .chargeApplied(c.getId())
            .subtract(preceding)
            .max(BigDecimal.ZERO)
            .min(i.getAmount());
    var pending = i.getAmount().subtract(paid);
    boolean changed = s.getTotal().compareTo(charges.originDebt(c.getId())) != 0;
    String status =
        !s.getActive()
            ? "HISTORICAL"
            : changed
                ? "REVIEW"
                : pending.signum() == 0
                    ? "PAID"
                    : i.getDueOn().isBefore(access.today())
                        ? "OVERDUE"
                        : paid.signum() > 0 ? "PARTIAL" : "PENDING";
    return new DueResponse(
        i.getId(),
        c.getId(),
        s.getId(),
        i.getPosition(),
        i.getDueOn(),
        c.getCurrency(),
        i.getAmount(),
        paid,
        pending,
        status,
        s.getActive(),
        s.getReason());
  }
}
