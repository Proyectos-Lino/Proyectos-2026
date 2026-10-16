package com.odontocare.finance.service;

import com.odontocare.audit.service.AuditService;
import com.odontocare.finance.dto.PaymentContracts.*;
import com.odontocare.finance.model.*;
import com.odontocare.finance.repository.*;
import com.odontocare.patients.repository.PatientRepository;
import com.odontocare.shared.pagination.*;
import com.odontocare.shared.web.ApiException;
import java.math.BigDecimal;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AccountQueryService {
  private final ChargeEntryRepository charges;
  private final MoneyMovementRepository movements;
  private final MoneyApplicationRepository applications;
  private final ChargeBalanceRepository balances;
  private final PatientRepository patients;
  private final AuditService audit;
  private final ChargeStateRepository states;
  private final FinanceOperationRepository operations;

  public AccountQueryService(
      ChargeEntryRepository charges,
      MoneyMovementRepository movements,
      MoneyApplicationRepository applications,
      ChargeBalanceRepository balances,
      PatientRepository patients,
      AuditService audit,
      FinanceOperationRepository operations,
      ChargeStateRepository states) {
    this.charges = charges;
    this.movements = movements;
    this.applications = applications;
    this.balances = balances;
    this.patients = patients;
    this.audit = audit;
    this.operations = operations;
    this.states = states;
  }

  @Transactional(isolation = org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
  public List<AccountSummary> summary(UUID patient) {
    patients.findById(patient).orElseThrow(ApiException::notFound);
    audit.record("ACCOUNT_READ", "PAYMENT", patient, "Consultó estado financiero");
    Set<String> currencies = new TreeSet<>(charges.currencies(patient));
    currencies.addAll(movements.currencies(patient));
    return currencies.stream().map(c -> activeSummary(patient, c)).toList();
  }

  @Transactional(isolation = org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
  public List<AccountSummary> historicalSummary(UUID patient) {
    patients.findById(patient).orElseThrow(ApiException::notFound);
    Set<String> currencies = new TreeSet<>(charges.currencies(patient));
    currencies.addAll(movements.currencies(patient));
    return currencies.stream().map(c -> summary(patient, c)).toList();
  }

  private AccountSummary activeSummary(UUID patient, String currency) {
    var historical = summary(patient, currency);
    var debt = states.activeTotal(patient, currency, false);
    var applied = states.activeTotal(patient, currency, true);
    return new AccountSummary(
        currency,
        debt,
        applied.add(historical.advance()),
        applied,
        debt.subtract(applied),
        historical.advance());
  }

  public AccountSummary summary(UUID patient, String currency) {
    var debt = charges.charges(patient, currency).add(charges.adjustments(patient, currency));
    var received = movements.received(patient, currency);
    var applied = applications.applied(patient, currency);
    return new AccountSummary(
        currency, debt, received, applied, debt.subtract(applied), received.subtract(applied));
  }

  @Transactional
  public OpenCharge charge(UUID id) {
    var c = balances.findById(id).orElseThrow(ApiException::notFound);
    return new OpenCharge(
        c.getId(),
        c.getDescription(),
        c.getCurrency(),
        c.getDebt(),
        c.getApplied(),
        c.getPending(),
        states.closed(id));
  }

  @Transactional
  public PageResponse<OpenCharge> charges(
      UUID patient, String currency, boolean pendingOnly, PageQuery query) {
    return charges(patient, currency, pendingOnly, false, null, query);
  }

  @Transactional
  public PageResponse<OpenCharge> charges(
      UUID patient,
      String currency,
      boolean pendingOnly,
      Boolean closed,
      UUID encounterId,
      PageQuery query) {
    var spec =
        SearchSpecifications.<ChargeBalance>text(query.getSearch(), "description")
            .and(SearchSpecifications.equal("patientId", patient))
            .and(SearchSpecifications.equal("currency", currency))
            .and(SearchSpecifications.equal("closed", closed))
            .and(SearchSpecifications.equal("encounterId", encounterId));
    if (pendingOnly) spec = spec.and((r, c, b) -> b.greaterThan(r.get("pending"), BigDecimal.ZERO));
    return PageResponse.of(
        balances
            .findAll(
                spec,
                query.pageable(Map.of("name", "description", "pending", "pending", "debt", "debt")))
            .map(
                c ->
                    new OpenCharge(
                        c.getId(),
                        c.getDescription(),
                        c.getCurrency(),
                        c.getDebt(),
                        c.getApplied(),
                        c.getPending(),
                        c.isClosed())));
  }

  @Transactional(isolation = org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
  public PageResponse<MovementResponse> movements(
      UUID patient,
      UUID original,
      UUID cashSession,
      String kind,
      String method,
      String currency,
      java.time.LocalDate from,
      java.time.LocalDate to,
      UUID chargeId,
      PageQuery query) {
    if (kind != null
        && !Set.of("PAYMENT", "REFUND", "REVERSAL", "EXPENSE", "EXPENSE_REVERSAL").contains(kind))
      throw ApiException.badRequest("Tipo de movimiento no válido.");
    if (method != null && !Set.of("CASH", "TRANSFER", "CARD", "OTHER").contains(method))
      throw ApiException.badRequest("Medio de pago no válido.");
    if (from != null && to != null && from.isAfter(to))
      throw ApiException.badRequest("Intervalo de fechas no válido.");
    var spec =
        SearchSpecifications.<MoneyMovement>text(
                query.getSearch(),
                "description",
                "reference",
                "supplier",
                "actorName",
                "receiptCode",
                "categoryName")
            .and(SearchSpecifications.equal("patientId", patient))
            .and(SearchSpecifications.equal("originalId", original))
            .and(SearchSpecifications.equal("cashSessionId", cashSession))
            .and(SearchSpecifications.equal("kind", kind))
            .and(SearchSpecifications.equal("method", method))
            .and(SearchSpecifications.equal("currency", currency));
    if (from != null)
      spec = spec.and((r, c, b) -> b.greaterThanOrEqualTo(r.get("occurredOn"), from));
    if (to != null) spec = spec.and((r, c, b) -> b.lessThanOrEqualTo(r.get("occurredOn"), to));
    UUID linkedCharge = null;
    if (chargeId != null) {
      var charge = charges.findById(chargeId).orElseThrow(ApiException::notFound);
      if (patient != null && !charge.getPatientId().equals(patient)) throw ApiException.notFound();
      linkedCharge = charge.getOriginalId() == null ? charge.getId() : charge.getOriginalId();
      UUID originCharge = linkedCharge;
      spec =
          spec.and(SearchSpecifications.equal("kind", "PAYMENT"))
              .and(
                  (r, q, b) -> {
                    var sub = q.subquery(UUID.class);
                    var allocation = sub.from(MoneyApplication.class);
                    sub.select(allocation.get("paymentId"))
                        .where(b.equal(allocation.get("chargeId"), originCharge));
                    return r.get("id").in(sub);
                  });
    }
    audit.record("MONEY_READ", "PAYMENT", patient, "Consultó movimientos de dinero");
    var page =
        movements.findAll(
            spec,
            query.pageable(
                Map.of(
                    "name",
                    "description",
                    "createdAt",
                    "createdAt",
                    "amount",
                    "amount",
                    "occurredOn",
                    "occurredOn")));
    Map<UUID, BigDecimal> byPayment = new HashMap<>();
    if (linkedCharge != null && !page.isEmpty())
      applications
          .chargePaymentAmounts(linkedCharge, page.stream().map(MoneyMovement::getId).toList())
          .forEach(a -> byPayment.put(a.getPaymentId(), a.getAmount()));
    return PageResponse.of(
        page.map(
            m ->
                response(
                    m,
                    chargeId == null ? null : byPayment.getOrDefault(m.getId(), BigDecimal.ZERO))));
  }

  @Transactional
  public PageResponse<ApplicationResponse> applications(
      UUID payment, UUID charge, PageQuery query) {
    var spec =
        SearchSpecifications.<MoneyApplication>text("")
            .and(SearchSpecifications.equal("paymentId", payment))
            .and(SearchSpecifications.equal("chargeId", charge));
    if (!query.getSearch().isBlank())
      spec =
          spec.and(
              (r, c, b) -> {
                var sub = c.subquery(UUID.class);
                var origin = sub.from(ChargeEntry.class);
                sub.select(origin.get("id"))
                    .where(
                        SearchSpecifications.<ChargeEntry>text(query.getSearch(), "description")
                            .toPredicate(origin, c, b));
                return r.get("chargeId").in(sub);
              });
    return PageResponse.of(
        applications
            .findAll(
                spec,
                query.pageable(
                    Map.of("name", "createdAt", "createdAt", "createdAt", "amount", "amount")))
            .map(
                a ->
                    new ApplicationResponse(
                        a.getId(),
                        a.getPaymentId(),
                        a.getChargeId(),
                        a.getOperationId(),
                        a.getAmount(),
                        charges.findById(a.getChargeId()).orElseThrow().getDescription(),
                        operations.findById(a.getOperationId()).orElseThrow().getReason(),
                        a.getCreatedAt())));
  }

  public MovementResponse response(MoneyMovement m) {
    return response(m, null);
  }

  private MovementResponse response(MoneyMovement m, BigDecimal chargeApplied) {
    boolean root = m.getOriginalId() == null;
    var effective = root ? m.getAmount().subtract(movements.returned(m.getId())) : m.getAmount();
    var applied =
        m.getKind().equals("PAYMENT") ? applications.paymentApplied(m.getId()) : BigDecimal.ZERO;
    return new MovementResponse(
        m.getId(),
        m.getPatientId(),
        m.getOriginalId(),
        m.getCashSessionId(),
        m.getKind(),
        m.getCurrency(),
        m.getAmount(),
        effective,
        applied,
        m.getKind().equals("PAYMENT") ? effective.subtract(applied) : BigDecimal.ZERO,
        m.getKind().equals("PAYMENT") ? applications.closedApplied(m.getId()) : BigDecimal.ZERO,
        m.getOccurredOn(),
        m.getMethod(),
        m.getReference(),
        m.getDescription(),
        m.getSupplier(),
        m.getCategoryName(),
        m.getActorName(),
        m.getReceiptCode(),
        m.getCreatedAt(),
        chargeApplied);
  }
}
