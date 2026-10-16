package com.odontocare.finance.service;

import com.odontocare.audit.service.AuditService;
import com.odontocare.clinical.dto.ClinicalContracts.*;
import com.odontocare.clinical.dto.EncounterWorkflowContracts.*;
import com.odontocare.clinical.repository.EncounterRepository;
import com.odontocare.clinical.service.*;
import com.odontocare.finance.dto.PaymentContracts.*;
import com.odontocare.finance.repository.*;
import com.odontocare.installation.repository.InstallationProfileRepository;
import com.odontocare.patients.repository.PatientRepository;
import com.odontocare.shared.web.ApiException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EncounterCheckoutService {
  private final EncounterRepository records;
  private final EncounterService encounters;
  private final ClinicalAccess access;
  private final ClinicalBillingService billing;
  private final ChargeEntryRepository charges;
  private final MoneyApplicationRepository applications;
  private final PatientRepository patients;
  private final FinanceOperationService operations;
  private final FinanceOperationRepository operationRecords;
  private final PaymentService payments;
  private final MoneyMovementRepository movements;
  private final AccountQueryService queries;
  private final ChargeClosureService closure;
  private final ChargeStateService states;
  private final InstallationProfileRepository profiles;
  private final AuditService audit;

  public EncounterCheckoutService(
      EncounterRepository records,
      EncounterService encounters,
      ClinicalAccess access,
      ClinicalBillingService billing,
      ChargeEntryRepository charges,
      MoneyApplicationRepository applications,
      PatientRepository patients,
      FinanceOperationService operations,
      FinanceOperationRepository operationRecords,
      PaymentService payments,
      MoneyMovementRepository movements,
      AccountQueryService queries,
      ChargeClosureService closure,
      ChargeStateService states,
      InstallationProfileRepository profiles,
      AuditService audit) {
    this.records = records;
    this.encounters = encounters;
    this.access = access;
    this.billing = billing;
    this.charges = charges;
    this.applications = applications;
    this.patients = patients;
    this.operations = operations;
    this.operationRecords = operationRecords;
    this.payments = payments;
    this.movements = movements;
    this.queries = queries;
    this.closure = closure;
    this.states = states;
    this.profiles = profiles;
    this.audit = audit;
  }

  @Transactional
  public CheckoutPreview preview(UUID id) {
    var encounter = encounters.get(id);
    access.requireDentist(encounter.dentistId());
    var currency = profiles.findById((short) 1).orElseThrow().getCurrency();
    var amount = BigDecimal.ZERO;
    int count = 0, included = 0;
    if (encounter.status().equals("DRAFT")) {
      for (var p : encounter.content().procedures()) {
        if (p.planItemId() != null) {
          included++;
          continue;
        }
        amount = amount.add(billing.unitPrice(p).multiply(BigDecimal.valueOf(p.quantity())));
        count++;
      }
    } else {
      for (var charge : charges.findByEncounterIdAndOriginalIdIsNull(id)) {
        if (states.closed(charge.getId())) continue;
        if (!charge.getCurrency().equals(currency))
          throw ApiException.conflict(
              "La atención usa otra moneda. Cobra desde la cuenta del paciente.");
        amount =
            amount.add(
                charges
                    .originDebt(charge.getId())
                    .subtract(applications.chargeApplied(charge.getId())));
        count++;
      }
      included =
          (int)
              encounter.content().procedures().stream().filter(p -> p.planItemId() != null).count();
    }
    return new CheckoutPreview(
        currency, amount, count, included, encounter.status().equals("FINAL"));
  }

  @Transactional
  public CheckoutResult checkout(UUID id, CheckoutRequest r) {
    patients.lockById(records.patientId(id).orElseThrow(ApiException::notFound)).orElseThrow();
    var encounter = encounters.get(id);
    access.requireDentist(encounter.dentistId());
    var body = List.of(id, r);
    var prior = operations.previous(r.requestKey(), "ENCOUNTER_CHECKOUT", body);
    if (prior.isPresent()) {
      var payment = operationRecords.findByRequestKey(key(r.requestKey(), "payment"));
      return new CheckoutResult(
          encounter,
          payment
              .map(p -> queries.response(movements.findById(p.getResultId()).orElseThrow()))
              .orElse(null),
          true);
    }
    if (encounter.version() != r.version())
      throw ApiException.conflict("La atención cambió. Revisa sus datos antes de cobrar.");
    var preview = preview(id);
    if (preview.individualCharges() == 0)
      throw ApiException.badRequest(
          "No hay cargos individuales abiertos para cobrar. Las sesiones incluidas se cobran desde"
              + " su plan en la cuenta del paciente.");
    if (preview.amount().compareTo(r.amount()) != 0)
      throw ApiException.conflict(
          "El importe pendiente cambió. Revisa el resumen antes de cobrar.");
    encounter = encounters.finish(id, new VersionRequest(r.version()));
    var rows =
        charges.findByEncounterIdAndOriginalIdIsNull(id).stream()
            .filter(c -> !states.closed(c.getId()))
            .toList();
    List<Allocation> allocations = new ArrayList<>();
    for (var c : rows) {
      var pending = charges.originDebt(c.getId()).subtract(applications.chargeApplied(c.getId()));
      if (pending.signum() > 0) allocations.add(new Allocation(c.getId(), pending));
    }
    var actual =
        allocations.stream().map(Allocation::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
    if (actual.compareTo(r.amount()) != 0)
      throw ApiException.conflict(
          "El importe cambió durante la finalización. Revisa el resumen antes de cobrar.");
    MovementResponse payment = null;
    if (r.amount().signum() > 0)
      payment =
          payments.register(
              new PaymentRequest(
                  key(r.requestKey(), "payment"),
                  encounter.patientId(),
                  r.amount(),
                  r.occurredOn(),
                  preview.currency(),
                  r.method(),
                  r.reference(),
                  "Cobro de atención: "
                      + encounter.reason().substring(0, Math.min(250, encounter.reason().length())),
                  allocations));
    for (var c : rows)
      closure.change(
          c.getId(),
          new ChargeStateRequest(
              key(r.requestKey(), "close:" + c.getId()), "Cierre por cobro completo de atención"),
          true);
    operations.record(
        r.requestKey(), "ENCOUNTER_CHECKOUT", body, id, "Atención finalizada y cobrada");
    audit.record(
        "ENCOUNTER_CHECKOUT",
        "ENCOUNTER",
        id,
        "Finalizó atención y registró su cobro con cargos cerrados");
    if (payment != null) payment = queries.response(movements.findById(payment.id()).orElseThrow());
    return new CheckoutResult(encounter, payment, false);
  }

  private UUID key(UUID root, String suffix) {
    return UUID.nameUUIDFromBytes((root + ":" + suffix).getBytes(StandardCharsets.UTF_8));
  }
}
