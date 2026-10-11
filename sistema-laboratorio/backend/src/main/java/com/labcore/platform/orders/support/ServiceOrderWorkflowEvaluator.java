package com.labcore.platform.orders;

import com.labcore.platform.workorders.WorkOrder;
import com.labcore.platform.workorders.WorkOrderRepository;
import com.labcore.platform.workorders.WorkOrderSample;
import com.labcore.platform.workorders.WorkOrderSampleRepository;
import com.labcore.platform.workorders.WorkOrderStatus;
import com.labcore.platform.workorders.WorkOrderTest;
import com.labcore.platform.workorders.WorkOrderTestRepository;
import com.labcore.platform.workorders.WorkOrderTestStatus;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Evaluates whether a service order has completed its technical lifecycle.
 *
 * <p>Work-order tests are the authoritative source for assignment and validation. Keeping this
 * calculation in one component prevents the API and future automated transitions from applying
 * different completion rules.
 */
@Component
public class ServiceOrderWorkflowEvaluator {

  private final WorkOrderRepository workOrderRepository;
  private final WorkOrderSampleRepository workOrderSampleRepository;
  private final WorkOrderTestRepository workOrderTestRepository;

  public ServiceOrderWorkflowEvaluator(
      WorkOrderRepository workOrderRepository,
      WorkOrderSampleRepository workOrderSampleRepository,
      WorkOrderTestRepository workOrderTestRepository) {
    this.workOrderRepository = workOrderRepository;
    this.workOrderSampleRepository = workOrderSampleRepository;
    this.workOrderTestRepository = workOrderTestRepository;
  }

  public ServiceOrderDtos.TechnicalProgress evaluate(
      ServiceOrder order, List<ServiceOrderSampleTest> requestedTests) {
    List<WorkOrder> activeWorkOrders =
        workOrderRepository.findByServiceOrderIdOrderByCreatedAtAsc(order.getId()).stream()
            .filter(workOrder -> workOrder.getStatus() != WorkOrderStatus.CANCELLED)
            .toList();
    List<WorkOrderSample> workOrderSamples = findWorkOrderSamples(activeWorkOrders);
    List<WorkOrderTest> workOrderTests = findWorkOrderTests(workOrderSamples);

    Set<UUID> requestedTestIds =
        requestedTests.stream()
            .map(ServiceOrderSampleTest::getId)
            .collect(java.util.stream.Collectors.toSet());
    Set<UUID> assignedTestIds = new HashSet<>();
    Set<UUID> validatedTestIds = new HashSet<>();

    for (WorkOrderTest test : workOrderTests) {
      if (!requestedTestIds.contains(test.getSourceSampleTestId())) {
        continue;
      }
      assignedTestIds.add(test.getSourceSampleTestId());
      if (test.getStatus() == WorkOrderTestStatus.VALIDATED) {
        validatedTestIds.add(test.getSourceSampleTestId());
      }
    }

    int totalTests = requestedTestIds.size();
    int pendingAssignment = totalTests - assignedTestIds.size();
    int pendingValidation = assignedTestIds.size() - validatedTestIds.size();
    int openWorkOrders =
        Math.toIntExact(
            activeWorkOrders.stream()
                .filter(workOrder -> workOrder.getStatus() != WorkOrderStatus.CLOSED)
                .count());
    List<String> blockingReasons =
        blockingReasons(
            totalTests,
            pendingAssignment,
            pendingValidation,
            activeWorkOrders.size(),
            openWorkOrders);
    boolean canFinalize =
        order.getStatus() == ServiceOrderStatus.IN_PROGRESS && blockingReasons.isEmpty();

    return new ServiceOrderDtos.TechnicalProgress(
        totalTests,
        assignedTestIds.size(),
        validatedTestIds.size(),
        pendingAssignment,
        pendingValidation,
        activeWorkOrders.size(),
        openWorkOrders,
        canFinalize,
        blockingReasons);
  }

  private List<WorkOrderSample> findWorkOrderSamples(List<WorkOrder> workOrders) {
    if (workOrders.isEmpty()) {
      return List.of();
    }
    return workOrderSampleRepository.findByWorkOrderIdIn(
        workOrders.stream().map(WorkOrder::getId).toList());
  }

  private List<WorkOrderTest> findWorkOrderTests(List<WorkOrderSample> samples) {
    if (samples.isEmpty()) {
      return List.of();
    }
    return workOrderTestRepository.findByWorkOrderSampleIdIn(
        samples.stream().map(WorkOrderSample::getId).toList());
  }

  private List<String> blockingReasons(
      int totalTests,
      int pendingAssignment,
      int pendingValidation,
      int totalWorkOrders,
      int openWorkOrders) {
    java.util.ArrayList<String> reasons = new java.util.ArrayList<>();
    if (totalTests == 0) {
      reasons.add("La orden no tiene ensayos solicitados.");
      return List.copyOf(reasons);
    }
    if (totalWorkOrders == 0) {
      reasons.add("Debe emitirse al menos una orden de trabajo.");
    }
    if (pendingAssignment > 0) {
      reasons.add("%d ensayo(s) aún no tienen una orden de trabajo.".formatted(pendingAssignment));
    }
    if (pendingValidation > 0) {
      reasons.add("%d ensayo(s) aún no están validados.".formatted(pendingValidation));
    }
    if (openWorkOrders > 0) {
      reasons.add("%d orden(es) de trabajo aún no están cerradas.".formatted(openWorkOrders));
    }
    return List.copyOf(reasons);
  }
}
