package com.labcore.platform.orders;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import com.labcore.platform.workorders.WorkOrder;
import com.labcore.platform.workorders.WorkOrderRepository;
import com.labcore.platform.workorders.WorkOrderSample;
import com.labcore.platform.workorders.WorkOrderSampleRepository;
import com.labcore.platform.workorders.WorkOrderTest;
import com.labcore.platform.workorders.WorkOrderTestRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ServiceOrderWorkflowEvaluatorTest {

  @Mock private WorkOrderRepository workOrderRepository;
  @Mock private WorkOrderSampleRepository workOrderSampleRepository;
  @Mock private WorkOrderTestRepository workOrderTestRepository;

  @Test
  void reportsAllTechnicalBlockersWithoutTreatingCancelledWorkAsActive() {
    ServiceOrder order = confirmedOrder();
    ServiceOrderSampleTest assignedTest = requestedTest();
    ServiceOrderSampleTest unassignedTest = requestedTest();
    WorkOrder activeWorkOrder = workOrder(order.getId());
    WorkOrder cancelledWorkOrder = workOrder(order.getId());
    cancelledWorkOrder.cancel("Reasignación técnica");
    WorkOrderSample workOrderSample =
        new WorkOrderSample(
            activeWorkOrder.getId(), UUID.randomUUID(), UUID.randomUUID(), "LAB-0001", 1);
    WorkOrderTest recordedTest =
        new WorkOrderTest(
            workOrderSample.getId(), assignedTest.getId(), UUID.randomUUID(), UUID.randomUUID());
    recordedTest.markRecorded();

    given(workOrderRepository.findByServiceOrderIdOrderByCreatedAtAsc(order.getId()))
        .willReturn(List.of(activeWorkOrder, cancelledWorkOrder));
    given(workOrderSampleRepository.findByWorkOrderIdIn(List.of(activeWorkOrder.getId())))
        .willReturn(List.of(workOrderSample));
    given(workOrderTestRepository.findByWorkOrderSampleIdIn(List.of(workOrderSample.getId())))
        .willReturn(List.of(recordedTest));

    ServiceOrderDtos.TechnicalProgress progress =
        evaluator().evaluate(order, List.of(assignedTest, unassignedTest));

    assertThat(progress.totalRequestedTests()).isEqualTo(2);
    assertThat(progress.assignedTests()).isEqualTo(1);
    assertThat(progress.validatedTests()).isZero();
    assertThat(progress.pendingAssignmentTests()).isEqualTo(1);
    assertThat(progress.pendingValidationTests()).isEqualTo(1);
    assertThat(progress.totalWorkOrders()).isEqualTo(1);
    assertThat(progress.openWorkOrders()).isEqualTo(1);
    assertThat(progress.canFinalize()).isFalse();
    assertThat(progress.blockingReasons())
        .hasSize(3)
        .anyMatch(reason -> reason.contains("orden de trabajo"))
        .anyMatch(reason -> reason.contains("validado"))
        .anyMatch(reason -> reason.contains("cerradas"));
  }

  @Test
  void allowsFinalizationOnlyWhenEveryTestIsValidatedAndEveryWorkOrderIsClosed() {
    ServiceOrder order = confirmedOrder();
    ServiceOrderSampleTest requestedTest = requestedTest();
    WorkOrder closedWorkOrder = workOrder(order.getId());
    closedWorkOrder.markValidated();
    closedWorkOrder.close();
    WorkOrderSample workOrderSample =
        new WorkOrderSample(
            closedWorkOrder.getId(), UUID.randomUUID(), UUID.randomUUID(), "LAB-0002", 1);
    WorkOrderTest validatedTest =
        new WorkOrderTest(
            workOrderSample.getId(), requestedTest.getId(), UUID.randomUUID(), UUID.randomUUID());
    validatedTest.markValidated();

    given(workOrderRepository.findByServiceOrderIdOrderByCreatedAtAsc(order.getId()))
        .willReturn(List.of(closedWorkOrder));
    given(workOrderSampleRepository.findByWorkOrderIdIn(List.of(closedWorkOrder.getId())))
        .willReturn(List.of(workOrderSample));
    given(workOrderTestRepository.findByWorkOrderSampleIdIn(List.of(workOrderSample.getId())))
        .willReturn(List.of(validatedTest));

    ServiceOrderDtos.TechnicalProgress progress =
        evaluator().evaluate(order, List.of(requestedTest));

    assertThat(progress.canFinalize()).isTrue();
    assertThat(progress.blockingReasons()).isEmpty();
    assertThat(progress.validatedTests()).isEqualTo(progress.totalRequestedTests());
    assertThat(progress.openWorkOrders()).isZero();
  }

  private ServiceOrderWorkflowEvaluator evaluator() {
    return new ServiceOrderWorkflowEvaluator(
        workOrderRepository, workOrderSampleRepository, workOrderTestRepository);
  }

  private ServiceOrder confirmedOrder() {
    ServiceOrder order =
        new ServiceOrder(
            "OS-2026-0099",
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
    return order;
  }

  private ServiceOrderSampleTest requestedTest() {
    return new ServiceOrderSampleTest(
        UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
  }

  private WorkOrder workOrder(UUID serviceOrderId) {
    return new WorkOrder(
        "OT-" + UUID.randomUUID(),
        serviceOrderId,
        UUID.randomUUID(),
        UUID.randomUUID(),
        Instant.now().plusSeconds(43_200));
  }
}
