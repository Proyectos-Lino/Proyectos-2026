package com.labcore.platform.orders;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ServiceOrderLifecycleTest {

  @Test
  void transitionsFromDraftToInProgressAndFinalized() {
    ServiceOrder order =
        new ServiceOrder(
            "OS-2026-0001",
            UUID.randomUUID(),
            UUID.randomUUID(),
            null,
            null,
            null,
            DeliveryMethod.EMAIL,
            null,
            Instant.now().plusSeconds(86_400),
            null,
            null,
            null,
            false,
            PostAnalysisCustody.RETAINED,
            null);

    assertThat(order.getStatus()).isEqualTo(ServiceOrderStatus.DRAFT);

    order.confirmForLaboratory();
    order.completeTechnicalProcess();

    assertThat(order.getStatus()).isEqualTo(ServiceOrderStatus.FINALIZED);
    assertThat(order.getConfirmedAt()).isNotNull();
    assertThat(order.getFinalizedAt()).isNotNull();
  }

  @Test
  void cancellationIsAllowedFromDraft() {
    ServiceOrder order =
        new ServiceOrder(
            "OS-2026-0002",
            UUID.randomUUID(),
            UUID.randomUUID(),
            null,
            null,
            null,
            DeliveryMethod.EMAIL,
            null,
            Instant.now().plusSeconds(86_400),
            null,
            null,
            null,
            false,
            PostAnalysisCustody.RETAINED,
            null);

    order.cancel("El cliente solicitó la anulación.");

    assertThat(order.getStatus()).isEqualTo(ServiceOrderStatus.CANCELLED);
    assertThat(order.getCancellationReason()).isEqualTo("El cliente solicitó la anulación.");
  }

  @Test
  void correctionPreservesFinalizedOrderUnlessTechnicalDefinitionsChange() {
    ServiceOrder order =
        new ServiceOrder(
            "OS-2026-0003",
            UUID.randomUUID(),
            UUID.randomUUID(),
            null,
            null,
            null,
            DeliveryMethod.EMAIL,
            null,
            Instant.now().plusSeconds(86_400),
            null,
            null,
            null,
            false,
            PostAnalysisCustody.RETAINED,
            null);
    order.confirmForLaboratory();
    order.completeTechnicalProcess();

    order.requestCommercialCorrection("Corregir razón social", "responsable@test");
    order.submitCommercialCorrection("comercial@test", "Se corrigió la identificación.");
    assertThat(order.getCorrectionChangeNote()).isEqualTo("Se corrigió la identificación.");
    order.approveCommercialCorrection("responsable@test");

    assertThat(order.getStatus()).isEqualTo(ServiceOrderStatus.FINALIZED);
    assertThat(order.getFinalizedAt()).isNotNull();

    order.requestCommercialCorrection("Cambiar ensayo", "responsable@test");
    order.registerCorrectionImpact("Ensayo reemplazado", true);
    order.submitCommercialCorrection("comercial@test", "Se corrigió la identificación.");
    order.approveCommercialCorrection("responsable@test");

    assertThat(order.getStatus()).isEqualTo(ServiceOrderStatus.IN_PROGRESS);
    assertThat(order.getFinalizedAt()).isNull();
  }
}
