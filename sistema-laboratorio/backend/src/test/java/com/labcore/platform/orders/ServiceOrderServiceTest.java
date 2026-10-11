package com.labcore.platform.orders;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.labcore.platform.catalog.CatalogService;
import com.labcore.platform.client.ClientService;
import com.labcore.platform.common.exception.BusinessRuleViolationException;
import com.labcore.platform.workorders.TechnicalSampleRepository;
import com.labcore.platform.workorders.WorkOrderRepository;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ServiceOrderServiceTest {

  @Mock private ServiceOrderRepository orderRepository;
  @Mock private ServiceOrderSampleRepository sampleRepository;
  @Mock private ServiceOrderSampleTestRepository requestedTestRepository;
  @Mock private ServiceOrderHistoryRepository historyRepository;
  @Mock private ClientService clientService;
  @Mock private CatalogService catalogService;
  @Mock private DocumentCodeGenerator codeGenerator;
  @Mock private WorkOrderRepository workOrderRepository;
  @Mock private TechnicalSampleRepository technicalSampleRepository;
  @Mock private ServiceOrderWorkflowEvaluator workflowEvaluator;
  @Mock private ServiceOrderDocumentService documentService;
  @Mock private ServiceOrderCorrectionImpactService correctionImpactService;

  @Test
  void confirmsOnlyCompleteDraftOrdersAndRecordsTheHandoff() {
    UUID orderId = UUID.randomUUID();
    ServiceOrder order = draftOrder();
    ServiceOrderSample sample = mock(ServiceOrderSample.class);
    ServiceOrderSampleTest test = mock(ServiceOrderSampleTest.class);
    UUID sampleId = UUID.randomUUID();
    given(orderRepository.findById(orderId)).willReturn(Optional.of(order));
    given(sample.getId()).willReturn(sampleId);
    given(sample.getProductName()).willReturn("Cacao en grano");
    given(sample.getSampleTypeId()).willReturn(UUID.randomUUID());
    given(test.getServiceOrderSampleId()).willReturn(sampleId);
    given(test.getTestDefinitionId()).willReturn(UUID.randomUUID());
    given(sampleRepository.findByServiceOrderIdAndActiveTrueOrderByCommercialSequenceAsc(orderId))
        .willReturn(java.util.List.of(sample));
    given(requestedTestRepository.findByServiceOrderSampleIdIn(java.util.List.of(sample.getId())))
        .willReturn(java.util.List.of(test));

    service().confirmOrder(orderId, "comercial@example.test");

    assertThat(order.getStatus()).isEqualTo(ServiceOrderStatus.IN_PROGRESS);
    assertThat(order.getConfirmedAt()).isNotNull();
    verify(historyRepository)
        .save(
            org.mockito.ArgumentMatchers.argThat(
                history ->
                    history.getAction() == ServiceOrderHistoryAction.CONFIRMED
                        && history.getPreviousStatus() == ServiceOrderStatus.DRAFT
                        && history.getNewStatus() == ServiceOrderStatus.IN_PROGRESS));
    verify(documentService).generateForConfirmation(orderId, "comercial@example.test");
  }

  @Test
  void preventsCancellingAnOrderWithIssuedWork() {
    UUID orderId = UUID.randomUUID();
    ServiceOrder order = draftOrder();
    order.confirmForLaboratory();
    given(orderRepository.findById(orderId)).willReturn(Optional.of(order));
    com.labcore.platform.workorders.WorkOrder workOrder =
        new com.labcore.platform.workorders.WorkOrder(
            "OT-2026-0010",
            orderId,
            UUID.randomUUID(),
            UUID.randomUUID(),
            Instant.now().plusSeconds(3_600));
    given(workOrderRepository.findByServiceOrderIdOrderByCreatedAtAsc(orderId))
        .willReturn(java.util.List.of(workOrder));

    assertThatThrownBy(
            () ->
                service()
                    .cancelOrder(
                        orderId,
                        new ServiceOrderDtos.CancelServiceOrderRequest("Solicitud del cliente"),
                        "comercial@example.test"))
        .isInstanceOf(BusinessRuleViolationException.class)
        .hasMessage(
            "Antes de anular la orden de servicio debe resolver sus órdenes de trabajo activas.");

    verify(workOrderRepository).findByServiceOrderIdOrderByCreatedAtAsc(eq(orderId));
  }

  @Test
  void rejectsFinalizationWhileTechnicalWorkIsIncomplete() {
    UUID orderId = UUID.randomUUID();
    ServiceOrder order = draftOrder();
    order.confirmForLaboratory();
    ServiceOrderSample sample = mock(ServiceOrderSample.class);
    ServiceOrderSampleTest test = mock(ServiceOrderSampleTest.class);
    given(sample.getId()).willReturn(UUID.randomUUID());
    given(orderRepository.findById(orderId)).willReturn(Optional.of(order));
    given(sampleRepository.findByServiceOrderIdAndActiveTrueOrderByCommercialSequenceAsc(orderId))
        .willReturn(java.util.List.of(sample));
    given(requestedTestRepository.findByServiceOrderSampleIdIn(java.util.List.of(sample.getId())))
        .willReturn(java.util.List.of(test));
    given(workflowEvaluator.evaluate(order, java.util.List.of(test)))
        .willReturn(
            new ServiceOrderDtos.TechnicalProgress(
                1,
                1,
                0,
                0,
                1,
                1,
                1,
                false,
                java.util.List.of("1 ensayo(s) aún no tiene(n) un resultado validado.")));

    assertThatThrownBy(() -> service().finalizeOrder(orderId, "comercial@example.test"))
        .isInstanceOf(BusinessRuleViolationException.class)
        .hasMessageContaining("resultado validado");

    assertThat(order.getStatus()).isEqualTo(ServiceOrderStatus.IN_PROGRESS);
    verify(historyRepository, never()).save(org.mockito.ArgumentMatchers.any());
  }

  @Test
  void finalizesAfterAllTestsAreValidatedAndWorkOrdersAreClosed() {
    UUID orderId = UUID.randomUUID();
    ServiceOrder order = draftOrder();
    order.confirmForLaboratory();
    ServiceOrderSample sample = mock(ServiceOrderSample.class);
    ServiceOrderSampleTest test = mock(ServiceOrderSampleTest.class);
    given(sample.getId()).willReturn(UUID.randomUUID());
    given(orderRepository.findById(orderId)).willReturn(Optional.of(order));
    given(sampleRepository.findByServiceOrderIdAndActiveTrueOrderByCommercialSequenceAsc(orderId))
        .willReturn(java.util.List.of(sample));
    given(requestedTestRepository.findByServiceOrderSampleIdIn(java.util.List.of(sample.getId())))
        .willReturn(java.util.List.of(test));
    given(workflowEvaluator.evaluate(order, java.util.List.of(test)))
        .willReturn(
            new ServiceOrderDtos.TechnicalProgress(1, 1, 1, 0, 0, 1, 0, true, java.util.List.of()));

    service().finalizeOrder(orderId, "comercial@example.test");

    assertThat(order.getStatus()).isEqualTo(ServiceOrderStatus.FINALIZED);
    verify(historyRepository)
        .save(
            org.mockito.ArgumentMatchers.argThat(
                history ->
                    history.getAction() == ServiceOrderHistoryAction.FINALIZED
                        && history.getPreviousStatus() == ServiceOrderStatus.IN_PROGRESS
                        && history.getNewStatus() == ServiceOrderStatus.FINALIZED
                        && history.getChangeNote().contains("1 de 1")));
  }

  @Test
  void rejectsRepeatedTestsWithinTheSameSample() {
    UUID orderId = UUID.randomUUID();
    UUID testId = UUID.randomUUID();
    ServiceOrder order = draftOrder();
    given(orderRepository.findById(orderId)).willReturn(Optional.of(order));
    given(requestedTestRepository.findByServiceOrderSampleIdIn(java.util.List.of()))
        .willReturn(java.util.List.of());

    ServiceOrderDtos.RequestedTestRequest requestedTest =
        new ServiceOrderDtos.RequestedTestRequest(
            null, UUID.randomUUID(), testId, UUID.randomUUID());
    ServiceOrderDtos.SampleRequest sample =
        new ServiceOrderDtos.SampleRequest(
            null,
            null,
            null,
            "Cacao en grano",
            null,
            UUID.randomUUID(),
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            false,
            null,
            java.util.List.of(requestedTest, requestedTest));

    assertThatThrownBy(
            () ->
                service()
                    .updateSamples(
                        orderId,
                        new ServiceOrderDtos.UpdateOrderSamplesRequest(
                            java.util.List.of(sample), false, null, null),
                        "comercial@example.test"))
        .isInstanceOf(BusinessRuleViolationException.class)
        .hasMessage("Una muestra no puede incluir el mismo ensayo más de una vez.");

    verify(sampleRepository, never()).save(org.mockito.ArgumentMatchers.any());
    verify(historyRepository, never()).save(org.mockito.ArgumentMatchers.any());
  }

  private ServiceOrder draftOrder() {
    return new ServiceOrder(
        "OS-2026-0010",
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
  }

  private ServiceOrderService service() {
    return new ServiceOrderService(
        orderRepository,
        sampleRepository,
        requestedTestRepository,
        historyRepository,
        clientService,
        catalogService,
        codeGenerator,
        workOrderRepository,
        technicalSampleRepository,
        workflowEvaluator,
        documentService,
        correctionImpactService);
  }
}
