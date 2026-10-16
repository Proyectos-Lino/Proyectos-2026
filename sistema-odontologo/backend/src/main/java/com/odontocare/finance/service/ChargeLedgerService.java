package com.odontocare.finance.service;

import com.odontocare.audit.service.AuditService;
import com.odontocare.clinical.service.ClinicalAccess;
import com.odontocare.finance.dto.FinanceContracts.*;
import com.odontocare.finance.model.ChargeBalance;
import com.odontocare.finance.model.ChargeEntry;
import com.odontocare.finance.repository.ChargeBalanceRepository;
import com.odontocare.finance.repository.ChargeEntryRepository;
import com.odontocare.patients.repository.PatientRepository;
import com.odontocare.shared.pagination.*;
import com.odontocare.shared.web.ApiException;
import com.odontocare.treatments.model.PlanItem;
import com.odontocare.treatments.repository.*;
import java.math.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

@Service
public class ChargeLedgerService {
  private final ChargeEntryRepository entries;
  private final ChargeBalanceRepository balances;
  private final PatientRepository patients;
  private final PlanItemRepository items;
  private final PlanSessionRepository sessions;
  private final TreatmentPlanRepository plans;
  private final AuditService audit;
  private final ChargeStateService states;
  private final ClinicalAccess access;
  private final ObjectMapper mapper;
  private final com.odontocare.finance.repository.MoneyApplicationRepository applications;

  public ChargeLedgerService(
      ChargeEntryRepository entries,
      PatientRepository patients,
      PlanItemRepository items,
      PlanSessionRepository sessions,
      TreatmentPlanRepository plans,
      AuditService audit,
      ClinicalAccess access,
      ObjectMapper mapper,
      com.odontocare.finance.repository.MoneyApplicationRepository applications,
      ChargeStateService states,
      ChargeBalanceRepository balances) {
    this.entries = entries;
    this.patients = patients;
    this.items = items;
    this.sessions = sessions;
    this.plans = plans;
    this.audit = audit;
    this.states = states;
    this.access = access;
    this.mapper = mapper;
    this.applications = applications;
    this.balances = balances;
  }

  public String fingerprint(Object value) {
    try {
      return HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256")
                  .digest(mapper.writeValueAsString(value).getBytes(StandardCharsets.UTF_8)));
    } catch (java.security.NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }

  @Transactional
  public void issue(
      UUID patientId,
      UUID planId,
      UUID itemId,
      UUID encounterId,
      String source,
      String description,
      String currency,
      BigDecimal price,
      int quantity,
      String reason) {
    if (entries.findBySourceKey(source).isPresent()) return;
    var entry = new ChargeEntry();
    entry.setPatientId(patientId);
    entry.setPlanId(planId);
    entry.setItemId(itemId);
    entry.setEncounterId(encounterId);
    entry.setSourceKey(source);
    entry.setFingerprint(fingerprint(List.of(source, price, quantity)));
    entry.setKind(planId == null ? "SERVICE" : "PLAN");
    entry.setDescription(description);
    entry.setCurrency(currency);
    entry.setUnitPrice(price);
    entry.setQuantity(quantity);
    entry.setAmount(
        price.multiply(BigDecimal.valueOf(quantity)).setScale(2, RoundingMode.UNNECESSARY));
    entry.setReason(reason);
    entry.setActorName(access.actor().getDisplayName());
    entries.saveAndFlush(entry);
    audit.record("CHARGE_ISSUED", "CHARGE", entry.getId(), "Generó cargo por " + entry.getKind());
  }

  @Transactional
  public EntryResponse adjust(UUID originalId, AdjustmentRequest request) {
    var original = entries.findById(originalId).orElseThrow(ApiException::notFound);
    patients.lockById(original.getPatientId()).orElseThrow();
    if (original.getOriginalId() != null)
      throw ApiException.badRequest("Selecciona el cargo original para ajustarlo.");
    String key = "adjust:" + request.requestKey(), hash = fingerprint(List.of(originalId, request));
    var prior = entries.findBySourceKey(key);
    if (prior.isPresent()) {
      if (!prior.get().getFingerprint().equals(hash))
        throw ApiException.conflict("La clave ya pertenece a otro ajuste.");
      return response(prior.get());
    }
    states.requireOpen(originalId);
    if (request.amount().signum() == 0)
      throw ApiException.badRequest("El ajuste debe cambiar el importe.");
    if (original.getPlanId() != null) {
      var plan = plans.lockById(original.getPlanId()).orElseThrow();
      if (plan.getStatus().equals("CANCELLED"))
        throw ApiException.conflict("Un plan cancelado conserva su ajuste de cancelación.");
    }
    BigDecimal result = entries.originDebt(originalId).add(request.amount());
    if (result.compareTo(applications.chargeApplied(originalId)) < 0)
      throw ApiException.badRequest(
          "Libera o devuelve el dinero aplicado antes de reducir el cargo.");
    if (result.signum() < 0)
      throw ApiException.badRequest("El ajuste no puede dejar el cargo negativo.");
    if (original.getItemId() != null) {
      var item = items.findById(original.getItemId()).orElseThrow();
      if (result.compareTo(performedValue(item)) < 0)
        throw ApiException.badRequest("Conserva la valoración de las sesiones ya realizadas.");
    }
    return response(
        appendAdjustment(original, request.amount(), "ADJUSTMENT", key, hash, request.reason()));
  }

  @Transactional
  public EntryResponse discount(
      UUID id, com.odontocare.finance.dto.PaymentContracts.DiscountRequest r, boolean cancel) {
    var original = entries.findById(id).orElseThrow(ApiException::notFound);
    patients.lockById(original.getPatientId()).orElseThrow();
    if (original.getOriginalId() != null)
      throw ApiException.badRequest("Selecciona el cargo original.");
    String key = (cancel ? "void:" : "discount:") + r.requestKey(),
        hash = fingerprint(List.of(id, r, cancel));
    var prior = entries.findBySourceKey(key);
    if (prior.isPresent()) {
      if (!prior.get().getFingerprint().equals(hash))
        throw ApiException.conflict("La clave corresponde a otro descuento.");
      return response(prior.get());
    }
    states.requireOpen(id);
    var current = entries.originDebt(id);
    if (r.amount().compareTo(current) > 0 || cancel && r.amount().compareTo(current) != 0)
      throw ApiException.badRequest(
          "El descuento no puede superar el cargo; la anulación es completa.");
    if (current.subtract(r.amount()).compareTo(applications.chargeApplied(id)) < 0)
      throw ApiException.badRequest(
          "Libera o devuelve el dinero aplicado antes de reducir el cargo.");
    return response(
        appendAdjustment(
            original,
            r.amount().negate(),
            "ADJUSTMENT",
            key,
            hash,
            (cancel ? "Anulación: " : "Descuento: ")
                + r.reason().substring(0, Math.min(480, r.reason().length()))));
  }

  public BigDecimal performedValue(PlanItem item) {
    return item.getUnitPrice()
        .multiply(BigDecimal.valueOf(item.getQuantity()))
        .multiply(BigDecimal.valueOf(sessions.completed(item.getId())))
        .divide(BigDecimal.valueOf(item.getSessions()), 2, RoundingMode.HALF_UP);
  }

  public void releaseUnperformed(PlanItem item, UUID key, String reason) {
    var original = entries.findBySourceKey("plan:" + item.getId()).orElseThrow();
    var current = entries.originDebt(original.getId());
    var retained = performedValue(item);
    var delta = current.subtract(retained).negate();
    if (retained.compareTo(applications.chargeApplied(original.getId())) < 0)
      throw ApiException.badRequest(
          "Libera o devuelve el dinero aplicado antes de cancelar este plan.");
    if (delta.signum() < 0)
      appendAdjustment(
          original,
          delta,
          "CANCELLATION",
          "cancel:" + key + ":" + item.getId(),
          fingerprint(List.of(key, item.getId(), delta)),
          reason);
  }

  private ChargeEntry appendAdjustment(
      ChargeEntry original,
      BigDecimal amount,
      String kind,
      String key,
      String hash,
      String reason) {
    states.requireOpen(original.getId());
    var entry = new ChargeEntry();
    entry.setPatientId(original.getPatientId());
    entry.setPlanId(original.getPlanId());
    entry.setItemId(original.getItemId());
    entry.setEncounterId(original.getEncounterId());
    entry.setOriginalId(original.getId());
    entry.setSourceKey(key);
    entry.setFingerprint(hash);
    entry.setKind(kind);
    entry.setDescription(original.getDescription());
    entry.setCurrency(original.getCurrency());
    entry.setAmount(amount.setScale(2, RoundingMode.UNNECESSARY));
    entry.setReason(reason.strip());
    entry.setActorName(access.actor().getDisplayName());
    entries.saveAndFlush(entry);
    audit.record(
        "CHARGE_ADJUSTED",
        "CHARGE",
        entry.getId(),
        "Registró " + kind + " con referencia original");
    return entry;
  }

  @Transactional(isolation = org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
  public PageResponse<EntryResponse> list(
      UUID patientId, UUID planId, UUID originId, String kind, String currency, PageQuery query) {
    return list(patientId, planId, originId, kind, currency, null, query);
  }

  @Transactional(isolation = org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
  public PageResponse<EntryResponse> list(
      UUID patientId,
      UUID planId,
      UUID originId,
      String kind,
      String currency,
      Boolean closed,
      PageQuery query) {
    if (kind != null && !Set.of("PLAN", "SERVICE", "ADJUSTMENT", "CANCELLATION").contains(kind))
      throw ApiException.badRequest("Tipo de movimiento no válido.");
    if (currency != null && !currency.matches("[A-Z]{3}"))
      throw ApiException.badRequest("Moneda no válida.");
    var spec =
        SearchSpecifications.<ChargeEntry>text(
                query.getSearch(), "description", "reason", "actorName")
            .and(SearchSpecifications.equal("patientId", patientId))
            .and(SearchSpecifications.equal("planId", planId))
            .and(SearchSpecifications.equal("kind", kind))
            .and(SearchSpecifications.equal("currency", currency));
    if (originId != null)
      spec =
          spec.and(
              (r, c, b) ->
                  b.or(b.equal(r.get("id"), originId), b.equal(r.get("originalId"), originId)));
    if (closed != null)
      spec =
          spec.and(
              (r, q, b) -> {
                var sub = q.subquery(UUID.class);
                var balance = sub.from(com.odontocare.finance.model.ChargeBalance.class);
                sub.select(balance.get("id")).where(b.equal(balance.get("closed"), closed));
                return b.coalesce(r.get("originalId"), r.get("id")).in(sub);
              });
    var page =
        entries.findAll(
            spec,
            query.pageable(
                Map.of("name", "description", "createdAt", "createdAt", "amount", "amount")));
    audit.record("CHARGES_READ", "CHARGE", patientId, "Consultó movimientos de deuda");
    var ids =
        page.stream()
            .map(c -> c.getOriginalId() == null ? c.getId() : c.getOriginalId())
            .distinct()
            .toList();
    Map<UUID, ChargeBalance> byId = new HashMap<>();
    if (!ids.isEmpty()) balances.findByIdIn(ids).forEach(b -> byId.put(b.getId(), b));
    return PageResponse.of(
        page.map(
            c -> response(c, byId.get(c.getOriginalId() == null ? c.getId() : c.getOriginalId()))));
  }

  @Transactional
  public List<DebtSummary> summary(UUID patientId) {
    patients.findById(patientId).orElseThrow(ApiException::notFound);
    audit.record("DEBT_READ", "CHARGE", patientId, "Consultó deuda por moneda");
    return entries.currencies(patientId).stream()
        .map(
            currency -> {
              var charges = entries.charges(patientId, currency);
              var adjustments = entries.adjustments(patientId, currency);
              return new DebtSummary(currency, charges, adjustments, charges.add(adjustments));
            })
        .toList();
  }

  public EntryResponse response(ChargeEntry c) {
    var balance =
        balances
            .findById(c.getOriginalId() == null ? c.getId() : c.getOriginalId())
            .orElseThrow(ApiException::notFound);
    return response(c, balance);
  }

  private EntryResponse response(ChargeEntry c, ChargeBalance balance) {
    if (balance == null) throw ApiException.notFound();
    boolean original = c.getOriginalId() == null;
    return new EntryResponse(
        c.getId(),
        c.getPatientId(),
        c.getPlanId(),
        c.getItemId(),
        c.getEncounterId(),
        c.getOriginalId(),
        c.getKind(),
        c.getDescription(),
        c.getCurrency(),
        c.getAmount(),
        c.getUnitPrice(),
        c.getQuantity(),
        c.getReason(),
        c.getActorName(),
        c.getCreatedAt(),
        balance.isClosed(),
        original ? balance.getPending() : null,
        original ? balance.getDebt() : null,
        original ? balance.getApplied() : null,
        original ? paymentStatus(balance) : null);
  }

  private String paymentStatus(ChargeBalance balance) {
    if (balance.getDebt().signum() == 0) return "NO_BALANCE";
    if (balance.getPending().signum() == 0) return "PAID";
    return balance.getApplied().signum() > 0 ? "PARTIAL" : "UNPAID";
  }
}
