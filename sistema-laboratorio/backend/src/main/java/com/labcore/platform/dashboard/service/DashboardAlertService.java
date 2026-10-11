package com.labcore.platform.dashboard;

import com.labcore.platform.orders.ServiceOrderRepository;
import com.labcore.platform.orders.ServiceOrderStatus;
import com.labcore.platform.security.ApplicationUser;
import com.labcore.platform.workorders.WorkOrder;
import com.labcore.platform.workorders.WorkOrderRepository;
import com.labcore.platform.workorders.WorkOrderSampleRepository;
import com.labcore.platform.workorders.WorkOrderStatus;
import com.labcore.platform.workorders.WorkOrderTest;
import com.labcore.platform.workorders.WorkOrderTestRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

@Service
class DashboardAlertService {
  private static final List<ServiceOrderStatus> ACTIVE_SERVICE_ORDER_STATUSES =
      List.of(ServiceOrderStatus.DRAFT, ServiceOrderStatus.IN_PROGRESS);
  private static final List<WorkOrderStatus> ACTIVE_WORK_ORDER_STATUSES =
      List.of(
          WorkOrderStatus.PENDING,
          WorkOrderStatus.IN_PROGRESS,
          WorkOrderStatus.RESULTS_RECORDED,
          WorkOrderStatus.VALIDATED);

  private final ServiceOrderRepository serviceOrderRepository;
  private final WorkOrderRepository workOrderRepository;
  private final WorkOrderSampleRepository workOrderSampleRepository;
  private final WorkOrderTestRepository workOrderTestRepository;
  private final DashboardRolePolicy rolePolicy;

  DashboardAlertService(
      ServiceOrderRepository serviceOrderRepository,
      WorkOrderRepository workOrderRepository,
      WorkOrderSampleRepository workOrderSampleRepository,
      WorkOrderTestRepository workOrderTestRepository,
      DashboardRolePolicy rolePolicy) {
    this.serviceOrderRepository = serviceOrderRepository;
    this.workOrderRepository = workOrderRepository;
    this.workOrderSampleRepository = workOrderSampleRepository;
    this.workOrderTestRepository = workOrderTestRepository;
    this.rolePolicy = rolePolicy;
  }

  List<DashboardDtos.Alert> alerts(ApplicationUser actor, UUID areaId) {
    Instant now = Instant.now();
    Instant warningLimit = now.plus(Duration.ofHours(48));
    List<DashboardDtos.Alert> alerts = new ArrayList<>();
    if (rolePolicy.canViewCommercial(actor)) {
      appendCommercialAlerts(alerts, now, warningLimit, areaId);
    }
    if (rolePolicy.canViewTechnical(actor)) {
      appendTechnicalAlerts(alerts, actor, now, warningLimit, areaId);
    }
    return alerts.stream()
        .sorted(Comparator.comparing(DashboardDtos.Alert::dueAt))
        .limit(12)
        .toList();
  }

  private void appendCommercialAlerts(
      List<DashboardDtos.Alert> alerts, Instant now, Instant warningLimit, UUID areaId) {
    serviceOrderRepository
        .findTop8ByStatusInAndPlannedDeliveryAtBeforeOrderByPlannedDeliveryAtAsc(
            ACTIVE_SERVICE_ORDER_STATUSES, warningLimit)
        .stream()
        .filter(order -> areaId == null || areaId.equals(order.getLaboratoryAreaId()))
        .forEach(
            order ->
                alerts.add(
                    new DashboardDtos.Alert(
                        "SERVICE_ORDER",
                        order.getPlannedDeliveryAt().isBefore(now) ? "CRITICAL" : "WARNING",
                        order.getId(),
                        order.getCode(),
                        order.getPlannedDeliveryAt().isBefore(now)
                            ? "Entrega comercial vencida"
                            : "Entrega comercial próxima",
                        "La fecha prometida al cliente requiere seguimiento.",
                        order.getPlannedDeliveryAt(),
                        "/orders?search=" + order.getCode())));
  }

  private void appendTechnicalAlerts(
      List<DashboardDtos.Alert> alerts,
      ApplicationUser actor,
      Instant now,
      Instant warningLimit,
      UUID areaId) {
    List<WorkOrder> candidates =
        rolePolicy.isRestrictedAnalyst(actor)
            ? visibleWorkOrders(actor.getId())
            : workOrderRepository
                .findTop8ByStatusInAndLaboratoryDueAtBeforeOrderByLaboratoryDueAtAsc(
                    ACTIVE_WORK_ORDER_STATUSES, warningLimit);
    candidates.stream()
        .filter(order -> areaId == null || areaId.equals(order.getLaboratoryAreaId()))
        .filter(order -> ACTIVE_WORK_ORDER_STATUSES.contains(order.getStatus()))
        .filter(order -> order.getLaboratoryDueAt().isBefore(warningLimit))
        .sorted(Comparator.comparing(WorkOrder::getLaboratoryDueAt))
        .limit(8)
        .forEach(
            order ->
                alerts.add(
                    new DashboardDtos.Alert(
                        "WORK_ORDER",
                        order.getLaboratoryDueAt().isBefore(now) ? "CRITICAL" : "WARNING",
                        order.getId(),
                        order.getCode(),
                        order.getLaboratoryDueAt().isBefore(now)
                            ? "Fecha técnica vencida"
                            : "Fecha técnica próxima",
                        "La orden técnica aún tiene trabajo pendiente.",
                        order.getLaboratoryDueAt(),
                        "/work-orders?search=" + order.getCode())));
  }

  private List<WorkOrder> visibleWorkOrders(UUID analystId) {
    Set<UUID> sampleIds =
        workOrderTestRepository.findByAssignedAnalystId(analystId).stream()
            .map(WorkOrderTest::getWorkOrderSampleId)
            .collect(Collectors.toSet());
    Set<UUID> workOrderIds =
        workOrderSampleRepository.findAllById(sampleIds).stream()
            .map(sample -> sample.getWorkOrderId())
            .collect(Collectors.toSet());
    return workOrderRepository.findAllById(workOrderIds);
  }
}
