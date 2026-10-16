package com.odontocare.treatments.service;

import com.odontocare.clinical.service.ClinicalAccess;
import com.odontocare.finance.dto.PaymentContracts.*;
import com.odontocare.finance.repository.ChargeEntryRepository;
import com.odontocare.finance.service.*;
import com.odontocare.shared.web.ApiException;
import com.odontocare.treatments.dto.TreatmentContracts.*;
import com.odontocare.treatments.model.TreatmentPlan;
import com.odontocare.treatments.repository.*;
import java.math.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.springframework.stereotype.Service;

/** Runs inside the acceptance transaction, using the existing cash/payment/installment services. */
@Service
public class PlanAgreementService {
  private final TreatmentPlanningRepository planning;
  private final PlanItemRepository items;
  private final ChargeEntryRepository charges;
  private final PaymentService payments;
  private final InstallmentService installments;
  private final ClinicalAccess access;

  public PlanAgreementService(
      TreatmentPlanningRepository planning,
      PlanItemRepository items,
      ChargeEntryRepository charges,
      PaymentService payments,
      InstallmentService installments,
      ClinicalAccess access) {
    this.planning = planning;
    this.items = items;
    this.charges = charges;
    this.payments = payments;
    this.installments = installments;
    this.access = access;
  }

  public PaymentAgreement validate(PaymentAgreement raw, BigDecimal total) {
    var agreement = raw == null ? PaymentAgreement.none() : raw;
    if (agreement.initialAmount() == null
        || agreement.initialAmount().signum() < 0
        || agreement.initialAmount().scale() > 2
        || agreement.initialAmount().compareTo(total) > 0
        || agreement.installments() == null
        || agreement.installments().size() > 59)
      throw ApiException.badRequest(
          "El pago inicial debe ser válido y no superar el total del presupuesto.");
    for (var due : agreement.installments())
      if (due == null
          || due.dueOn() == null
          || due.amount() == null
          || due.amount().signum() <= 0
          || due.amount().scale() > 2)
        throw ApiException.badRequest(
            "Cada cuota necesita fecha e importe positivo con dos decimales.");
    var sum =
        agreement.installments().stream()
            .map(AgreedInstallment::amount)
            .reduce(agreement.initialAmount(), BigDecimal::add);
    if (!agreement.installments().isEmpty() && sum.compareTo(total) != 0)
      throw ApiException.badRequest(
          "El pago inicial y las cuotas deben sumar el total del presupuesto.");
    return new PaymentAgreement(
        agreement.initialAmount().setScale(2),
        agreement.installments().stream()
            .sorted(Comparator.comparing(AgreedInstallment::dueOn))
            .toList());
  }

  public void apply(TreatmentPlan plan, InitialCollection collection) {
    var agreement = validate(planning.agreement(plan.getId()), items.total(plan.getId()));
    var lines = items.findByPlanIdOrderByPosition(plan.getId());
    var total = items.total(plan.getId());
    if (agreement.initialAmount().signum() > 0) {
      require("PAYMENTS_WRITE");
      if (collection == null
          || !Boolean.TRUE.equals(collection.received())
          || collection.occurredOn() == null
          || collection.method() == null
          || !Set.of("CASH", "TRANSFER", "CARD", "OTHER").contains(collection.method())
          || collection.reference() == null)
        throw ApiException.badRequest(
            "Confirma que recibiste el pago inicial e indica fecha y medio de pago.");
      payments.requireDate(collection.occurredOn());
      if (agreement.installments().stream()
          .anyMatch(d -> d.dueOn().isBefore(collection.occurredOn())))
        throw ApiException.badRequest(
            "Las cuotas no pueden preceder al pago inicial; revisa el acuerdo antes de aceptar.");
      var allocations = new ArrayList<Allocation>();
      var remaining = agreement.initialAmount();
      for (var line : lines) {
        var charge = charges.findBySourceKey("plan:" + line.getId()).orElseThrow();
        var portion = remaining.min(charge.getAmount());
        if (portion.signum() > 0) {
          allocations.add(new Allocation(charge.getId(), portion));
          remaining = remaining.subtract(portion);
        }
      }
      var result =
          payments.register(
              new PaymentRequest(
                  key(plan.getId(), "initial"),
                  plan.getPatientId(),
                  agreement.initialAmount(),
                  collection.occurredOn(),
                  plan.getCurrency(),
                  collection.method(),
                  collection.reference(),
                  "Pago inicial del plan " + plan.getCode(),
                  allocations));
      planning.payment(plan.getId(), result.id());
    }
    if (!agreement.installments().isEmpty()) {
      require("PAYMENTS_WRITE");
      var all = new ArrayList<DueRequest>();
      if (agreement.initialAmount().signum() > 0)
        all.add(new DueRequest(collection.occurredOn(), agreement.initialAmount()));
      agreement.installments().forEach(d -> all.add(new DueRequest(d.dueOn(), d.amount())));
      if (all.stream()
              .map(DueRequest::amount)
              .reduce(BigDecimal.ZERO, BigDecimal::add)
              .compareTo(total)
          != 0)
        throw ApiException.badRequest("El calendario acordado no distribuye el importe completo.");
      int cursor = 0;
      BigDecimal left = all.getFirst().amount();
      for (var line : lines) {
        var charge = charges.findBySourceKey("plan:" + line.getId()).orElseThrow();
        var remaining = charge.getAmount();
        var dues = new ArrayList<DueRequest>();
        while (remaining.signum() > 0) {
          var portion = remaining.min(left);
          dues.add(new DueRequest(all.get(cursor).dueOn(), portion));
          remaining = remaining.subtract(portion);
          left = left.subtract(portion);
          if (left.signum() == 0 && cursor + 1 < all.size()) {
            cursor++;
            left = all.get(cursor).amount();
          }
        }
        if (!dues.isEmpty())
          installments.schedule(
              new ScheduleRequest(
                  key(plan.getId(), "dues:" + line.getId()),
                  charge.getId(),
                  "Cuotas acordadas del plan " + plan.getCode(),
                  dues));
      }
    }
  }

  private void require(String permission) {
    if (!access.actor().getPermissions().contains(permission)) throw ApiException.forbidden();
  }

  private UUID key(UUID plan, String operation) {
    return UUID.nameUUIDFromBytes(
        ("plan-acceptance:" + plan + ":" + operation).getBytes(StandardCharsets.UTF_8));
  }
}
