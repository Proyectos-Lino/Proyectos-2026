package com.odontocare.treatments.service;

import com.odontocare.treatments.dto.TreatmentContracts.*;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Keep replay keys compatible with requests saved before planning fields existed. */
@Component
public class TreatmentRequestFingerprint {
  public Object payload(Object request) {
    if (request instanceof ActionRequest r && r.initialCollection() == null)
      return new LegacyAction(
          r.version(), r.requestKey(), r.reason(), r.acceptedBy(), r.releaseUnperformed());
    if (request instanceof PlanRequest r
        && r.paymentAgreement() == null
        && r.items().stream().allMatch(this::legacy))
      return new LegacyPlan(
          r.patientId(),
          r.dentistId(),
          r.title(),
          r.conditions(),
          r.items().stream().map(this::item).toList(),
          r.version(),
          r.requestKey());
    if (request instanceof AdditionalRequest r && legacy(r.item()))
      return new LegacyAdditional(r.version(), r.requestKey(), r.reason(), item(r.item()));
    return request;
  }

  private boolean legacy(ItemRequest item) {
    return item.durationMinutes() == null && item.scheduling() == null;
  }

  private LegacyItem item(ItemRequest r) {
    return new LegacyItem(
        r.serviceId(), r.description(), r.tooth(), r.quantity(), r.sessions(), r.unitPrice());
  }

  private record LegacyAction(
      Long version,
      UUID requestKey,
      String reason,
      String acceptedBy,
      Boolean releaseUnperformed) {}

  private record LegacyItem(
      UUID serviceId,
      String description,
      Integer tooth,
      Integer quantity,
      Integer sessions,
      BigDecimal unitPrice) {}

  private record LegacyPlan(
      UUID patientId,
      UUID dentistId,
      String title,
      String conditions,
      List<LegacyItem> items,
      Long version,
      UUID requestKey) {}

  private record LegacyAdditional(Long version, UUID requestKey, String reason, LegacyItem item) {}
}
