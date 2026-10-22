package com.odontocare.finance.service;

import com.odontocare.audit.service.AuditService;
import com.odontocare.clinical.service.ClinicalAccess;
import com.odontocare.finance.dto.PaymentContracts.*;
import com.odontocare.finance.model.*;
import com.odontocare.finance.repository.*;
import com.odontocare.patients.repository.PatientRepository;
import com.odontocare.shared.web.ApiException;
import java.math.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PaymentService {
  private final MoneyMovementRepository movements;
  private final MoneyApplicationRepository applications;
  private final ChargeEntryRepository charges;
  private final PatientRepository patients;
  private final FinanceOperationService operations;
  private final CashService cash;
  private final AccountQueryService queries;
  private final FinancialDocumentService documents;
  private final ClinicalAccess access;
  private final AuditService audit;
  private final ChargeStateService states;

  public PaymentService(
      MoneyMovementRepository movements,
      MoneyApplicationRepository applications,
      ChargeEntryRepository charges,
      PatientRepository patients,
      FinanceOperationService operations,
      CashService cash,
      AccountQueryService queries,
      FinancialDocumentService documents,
      ClinicalAccess access,
      AuditService audit,
      ChargeStateService states) {
    this.movements = movements;
    this.applications = applications;
    this.charges = charges;
    this.patients = patients;
    this.operations = operations;
    this.cash = cash;
    this.queries = queries;
    this.documents = documents;
    this.access = access;
    this.audit = audit;
    this.states = states;
  }

  public void requireDate(java.time.LocalDate date) {
    if (date.isAfter(access.today()))
      throw ApiException.badRequest("La fecha del movimiento no puede estar en el futuro.");
  }

  @Transactional
  public MovementResponse register(PaymentRequest r) {
    FinanceMoney.requireCurrency(r.currency());
    var patient = patients.lockById(r.patientId()).orElseThrow(ApiException::notFound);
    var prior = operations.previous(r.requestKey(), "PAYMENT", r);
    if (prior.isPresent())
      return queries.response(movements.findById(prior.get().getResultId()).orElseThrow());
    if (patient.getProvisional())
      throw ApiException.badRequest("Completa la ficha del paciente antes de cobrar.");
    requireDate(r.occurredOn());
    var session = cash.forMovement(r.currency(), r.method(), r.occurredOn(), false, r.amount());
    var m = new MoneyMovement();
    m.setPatientId(r.patientId());
    m.setKind("PAYMENT");
    m.setCurrency(r.currency());
    m.setAmount(r.amount());
    m.setOccurredOn(r.occurredOn());
    m.setMethod(r.method());
    m.setReference(r.reference().strip());
    m.setDescription(r.description().strip());
    m.setSupplier("");
    m.setCategoryName("");
    m.setActorName(access.actor().getDisplayName());
    if (session != null) m.setCashSessionId(session.getId());
    m.setReceiptCode(documents.nextCode());
    movements.saveAndFlush(m);
    var op = operations.record(r.requestKey(), "PAYMENT", r, m.getId(), r.description());
    apply(m, r.allocations(), op, false);
    documents.receipt(m, r.allocations(), r.description());
    audit.record("PAYMENT_REGISTERED", "PAYMENT", m.getId(), "Registró ingreso con constancia");
    return queries.response(m);
  }

  private BigDecimal available(MoneyMovement m) {
    return m.getAmount()
        .subtract(movements.returned(m.getId()))
        .subtract(applications.paymentApplied(m.getId()));
  }

  private void apply(MoneyMovement m, List<Allocation> rows, FinanceOperation op, boolean release) {
    Set<UUID> ids = new HashSet<>();
    var total = BigDecimal.ZERO;
    for (var row : rows) {
      if (!ids.add(row.chargeId()))
        throw ApiException.badRequest("Un cargo no puede aparecer dos veces.");
      var c = charges.findById(row.chargeId()).orElseThrow(ApiException::notFound);
      if (c.getOriginalId() != null
          || !c.getPatientId().equals(m.getPatientId())
          || !c.getCurrency().equals(m.getCurrency()))
        throw ApiException.badRequest(
            "El cargo debe ser original, del paciente y de la misma moneda.");
      states.requireOpen(c.getId());
      var limit =
          release
              ? applications.pairApplied(m.getId(), c.getId())
              : charges.originDebt(c.getId()).subtract(applications.chargeApplied(c.getId()));
      if (row.amount().compareTo(limit) > 0)
        throw ApiException.badRequest(
            release
                ? "La liberación supera el importe aplicado."
                : "La aplicación supera el saldo pendiente del cargo.");
      total = total.add(row.amount());
    }
    if (!release && total.compareTo(available(m)) > 0)
      throw ApiException.badRequest("Las aplicaciones superan el anticipo disponible.");
    for (var row : rows) {
      var a = new MoneyApplication();
      a.setPatientId(m.getPatientId());
      a.setPaymentId(m.getId());
      a.setChargeId(row.chargeId());
      a.setOperationId(op.getId());
      a.setAmount(release ? row.amount().negate() : row.amount());
      applications.save(a);
    }
    applications.flush();
  }

  @Transactional
  public MovementResponse apply(UUID id, ApplyRequest r, boolean release) {
    var m = movements.findById(id).orElseThrow(ApiException::notFound);
    if (!m.getKind().equals("PAYMENT"))
      throw ApiException.badRequest("Selecciona un pago original.");
    patients.lockById(m.getPatientId()).orElseThrow();
    String action = release ? "RELEASE" : "APPLY";
    var body = List.of(id, r);
    if (operations.previous(r.requestKey(), action, body).isPresent()) return queries.response(m);
    var op = operations.record(r.requestKey(), action, body, id, r.reason());
    apply(m, r.allocations(), op, release);
    audit.record(release ? "PAYMENT_RELEASED" : "ADVANCE_APPLIED", "PAYMENT", id, r.reason());
    return queries.response(m);
  }

  @Transactional
  public MovementResponse correct(UUID id, CorrectionRequest r, boolean reverse) {
    var original = movements.findById(id).orElseThrow(ApiException::notFound);
    if (!original.getKind().equals("PAYMENT"))
      throw ApiException.badRequest("Selecciona un pago original.");
    patients.lockById(original.getPatientId()).orElseThrow();
    String action = reverse ? "REVERSAL" : "REFUND";
    var body = List.of(id, r);
    var prior = operations.previous(r.requestKey(), action, body);
    if (prior.isPresent())
      return queries.response(movements.findById(prior.get().getResultId()).orElseThrow());
    requireDate(r.occurredOn());
    var effective = original.getAmount().subtract(movements.returned(id));
    if (r.amount().compareTo(effective) > 0 || reverse && r.amount().compareTo(effective) != 0)
      throw ApiException.badRequest(
          "La corrección debe respetar el importe recibido vigente; la reversión es completa.");
    var session =
        cash.forMovement(
            original.getCurrency(), original.getMethod(), r.occurredOn(), true, r.amount());
    var m = new MoneyMovement();
    m.setPatientId(original.getPatientId());
    m.setOriginalId(id);
    m.setKind(action);
    m.setCurrency(original.getCurrency());
    m.setAmount(r.amount());
    m.setOccurredOn(r.occurredOn());
    m.setMethod(original.getMethod());
    m.setReference(original.getReceiptCode());
    m.setDescription(
        (action.equals("REFUND") ? "Devolución: " : "Reversión: ")
            + original
                .getDescription()
                .substring(0, Math.min(260, original.getDescription().length())));
    m.setSupplier("");
    m.setCategoryName("");
    m.setActorName(access.actor().getDisplayName());
    if (session != null) m.setCashSessionId(session.getId());
    m.setReceiptCode(documents.nextCode());
    movements.saveAndFlush(m);
    var op = operations.record(r.requestKey(), action, body, m.getId(), r.reason());
    var releases =
        reverse
            ? applications.appliedChargeIds(id).stream()
                .map(c -> new Allocation(c, applications.pairApplied(id, c)))
                .toList()
            : r.releases();
    var released =
        releases.stream().map(Allocation::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
    var unallocated = effective.subtract(applications.paymentApplied(id));
    if (!reverse && released.compareTo(r.amount()) > 0)
      throw ApiException.badRequest("La liberación no puede superar la devolución.");
    if (unallocated.add(released).compareTo(r.amount()) < 0)
      throw ApiException.badRequest(
          "Libera las aplicaciones necesarias antes de devolver ese importe.");
    apply(original, releases, op, true);
    documents.receipt(m, releases, r.reason());
    audit.record("PAYMENT_" + action, "PAYMENT", m.getId(), r.reason());
    return queries.response(m);
  }
}
