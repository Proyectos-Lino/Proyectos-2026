package com.labcore.platform.orders;

import com.labcore.platform.common.exception.BusinessRuleViolationException;
import com.labcore.platform.common.exception.ResourceNotFoundException;
import com.labcore.platform.reports.LaboratoryReportService;
import com.labcore.platform.security.ApplicationUser;
import com.labcore.platform.security.ApplicationUserRepository;
import com.labcore.platform.security.RoleCode;
import com.labcore.platform.workorders.WorkOrder;
import com.labcore.platform.workorders.WorkOrderDocumentService;
import com.labcore.platform.workorders.WorkOrderHistory;
import com.labcore.platform.workorders.WorkOrderHistoryAction;
import com.labcore.platform.workorders.WorkOrderHistoryRepository;
import com.labcore.platform.workorders.WorkOrderRepository;
import com.labcore.platform.workorders.WorkOrderStatus;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class ServiceOrderCorrectionWorkflowService {

  private final ServiceOrderService serviceOrderService;
  private final ServiceOrderCorrectionImpactService impactService;
  private final ServiceOrderHistoryRepository serviceOrderHistoryRepository;
  private final WorkOrderRepository workOrderRepository;
  private final WorkOrderHistoryRepository workOrderHistoryRepository;
  private final ApplicationUserRepository userRepository;
  private final LaboratoryReportService reportService;
  private final ServiceOrderDocumentService serviceOrderDocumentService;
  private final WorkOrderDocumentService workOrderDocumentService;

  public ServiceOrderCorrectionWorkflowService(
      ServiceOrderService serviceOrderService,
      ServiceOrderCorrectionImpactService impactService,
      ServiceOrderHistoryRepository serviceOrderHistoryRepository,
      WorkOrderRepository workOrderRepository,
      WorkOrderHistoryRepository workOrderHistoryRepository,
      ApplicationUserRepository userRepository,
      LaboratoryReportService reportService,
      ServiceOrderDocumentService serviceOrderDocumentService,
      WorkOrderDocumentService workOrderDocumentService) {
    this.serviceOrderService = serviceOrderService;
    this.impactService = impactService;
    this.serviceOrderHistoryRepository = serviceOrderHistoryRepository;
    this.workOrderRepository = workOrderRepository;
    this.workOrderHistoryRepository = workOrderHistoryRepository;
    this.userRepository = userRepository;
    this.reportService = reportService;
    this.serviceOrderDocumentService = serviceOrderDocumentService;
    this.workOrderDocumentService = workOrderDocumentService;
  }

  public ServiceOrderDtos.ServiceOrderDetail requestCorrection(
      UUID orderId,
      ServiceOrderDtos.RequestCommercialCorrectionRequest request,
      String actorEmail) {
    ApplicationUser actor = currentUser(actorEmail);
    ServiceOrder order = serviceOrderService.getRequired(orderId);
    List<WorkOrder> workOrders =
        workOrderRepository.findByServiceOrderIdOrderByCreatedAtAsc(orderId);
    ensureTechnicalAuthority(actor, workOrders);
    if (order.getStatus() != ServiceOrderStatus.IN_PROGRESS
        && order.getStatus() != ServiceOrderStatus.FINALIZED) {
      throw new BusinessRuleViolationException(
          "Solo una orden en laboratorio o finalizada puede devolverse a corrección comercial.");
    }

    ServiceOrderStatus previousStatus = order.getStatus();
    String reason = request.reason().trim();
    order.requestCommercialCorrection(reason, actorEmail);
    serviceOrderHistoryRepository.save(
        new ServiceOrderHistory(
            orderId,
            ServiceOrderHistoryAction.COMMERCIAL_CORRECTION_REQUESTED,
            previousStatus,
            order.getStatus(),
            "Devuelta a atención comercial. Motivo: " + reason,
            actorEmail));
    workOrders.forEach(workOrder -> pauseWorkOrder(workOrder, reason, actorEmail));
    reportService.cancelOpenReportsForCommercialCorrection(
        orderId, "Corrección comercial solicitada: " + reason, actorEmail);
    return serviceOrderService.getDetail(orderId);
  }

  @Transactional(readOnly = true)
  public ServiceOrderDtos.CorrectionImpactResponse assessImpact(
      UUID orderId, ServiceOrderDtos.UpdateOrderSamplesRequest request, String actorEmail) {
    ApplicationUser actor = currentUser(actorEmail);
    if (!hasRole(actor, RoleCode.ADMIN) && !hasRole(actor, RoleCode.COMMERCIAL)) {
      throw new BusinessRuleViolationException(
          "Solo atención comercial o administración puede evaluar la corrección.");
    }
    ServiceOrder order = serviceOrderService.getRequired(orderId);
    ensureCommercialCorrection(order);
    return impactService.assess(order, request);
  }

  public ServiceOrderDtos.ServiceOrderDetail submitCorrection(
      UUID orderId, ServiceOrderDtos.SubmitCommercialCorrectionRequest request, String actorEmail) {
    ServiceOrder order = serviceOrderService.getRequired(orderId);
    ensureCommercialCorrection(order);
    ensureCompleteDefinition(orderId);
    ServiceOrderStatus previousStatus = order.getStatus();
    String changeNote = request.changeNote().trim();
    order.submitCommercialCorrection(actorEmail, changeNote);
    serviceOrderHistoryRepository.save(
        new ServiceOrderHistory(
            orderId,
            ServiceOrderHistoryAction.COMMERCIAL_CORRECTION_SUBMITTED,
            previousStatus,
            order.getStatus(),
            "Corrección enviada a revisión técnica. " + changeNote,
            actorEmail));
    return serviceOrderService.getDetail(orderId);
  }

  public ServiceOrderDtos.ServiceOrderDetail approveCorrection(UUID orderId, String actorEmail) {
    ApplicationUser actor = currentUser(actorEmail);
    ServiceOrder order = serviceOrderService.getRequired(orderId);
    List<WorkOrder> workOrders =
        workOrderRepository.findByServiceOrderIdOrderByCreatedAtAsc(orderId);
    ensureTechnicalAuthority(actor, workOrders);
    if (order.getStatus() != ServiceOrderStatus.CORRECTION_REVIEW) {
      throw new BusinessRuleViolationException(
          "La corrección todavía no fue enviada a revisión técnica.");
    }

    ServiceOrderStatus previousStatus = order.getStatus();
    order.approveCommercialCorrection(actorEmail);
    serviceOrderHistoryRepository.save(
        new ServiceOrderHistory(
            orderId,
            ServiceOrderHistoryAction.COMMERCIAL_CORRECTION_APPROVED,
            previousStatus,
            order.getStatus(),
            order.isCorrectionRequiresTechnicalWork()
                ? "Corrección aprobada. La orden vuelve al laboratorio con trabajo técnico pendiente."
                : "Corrección aprobada sin repetir resultados; se conserva el avance técnico previo.",
            actorEmail));
    workOrders.forEach(workOrder -> resumeWorkOrder(workOrder, actorEmail));
    serviceOrderDocumentService.regenerateAfterCorrection(orderId, actorEmail);
    workOrders.stream()
        .filter(workOrder -> workOrder.getStatus() != WorkOrderStatus.CANCELLED)
        .forEach(
            workOrder ->
                workOrderDocumentService.regenerateAfterCorrection(workOrder.getId(), actorEmail));
    return serviceOrderService.getDetail(orderId);
  }

  private void pauseWorkOrder(WorkOrder workOrder, String reason, String actorEmail) {
    WorkOrderStatus previous = workOrder.getStatus();
    workOrder.pauseForCommercialCorrection();
    if (previous != workOrder.getStatus()) {
      workOrderHistoryRepository.save(
          new WorkOrderHistory(
              workOrder.getId(),
              WorkOrderHistoryAction.COMMERCIAL_CORRECTION_PAUSED,
              previous,
              workOrder.getStatus(),
              "Operación pausada por corrección comercial de la orden de servicio. Motivo: "
                  + reason,
              actorEmail));
    }
  }

  private void resumeWorkOrder(WorkOrder workOrder, String actorEmail) {
    WorkOrderStatus previous = workOrder.getStatus();
    workOrder.resumeAfterCommercialCorrection();
    if (previous != workOrder.getStatus()) {
      workOrderHistoryRepository.save(
          new WorkOrderHistory(
              workOrder.getId(),
              WorkOrderHistoryAction.COMMERCIAL_CORRECTION_RESUMED,
              previous,
              workOrder.getStatus(),
              "Corrección comercial revisada. Se restablece el estado técnico conservado.",
              actorEmail));
    }
  }

  private void ensureCompleteDefinition(UUID orderId) {
    List<ServiceOrderSample> samples = serviceOrderService.getSamples(orderId);
    if (samples.isEmpty()) {
      throw new BusinessRuleViolationException("La orden debe conservar al menos una muestra.");
    }
    List<ServiceOrderSampleTest> tests =
        serviceOrderService
            .getRequestedTests(samples.stream().map(ServiceOrderSample::getId).toList())
            .stream()
            .filter(test -> test.getStatus() != RequestedTestStatus.CANCELLED)
            .toList();
    if (samples.stream()
        .anyMatch(
            sample ->
                tests.stream()
                    .noneMatch(test -> test.getServiceOrderSampleId().equals(sample.getId())))) {
      throw new BusinessRuleViolationException(
          "Cada muestra debe conservar al menos un ensayo solicitado.");
    }
  }

  private void ensureCommercialCorrection(ServiceOrder order) {
    if (order.getStatus() != ServiceOrderStatus.COMMERCIAL_CORRECTION) {
      throw new BusinessRuleViolationException(
          "La orden no se encuentra habilitada para corrección comercial.");
    }
  }

  private void ensureTechnicalAuthority(ApplicationUser actor, List<WorkOrder> workOrders) {
    if (hasRole(actor, RoleCode.ADMIN)) return;
    if (!hasRole(actor, RoleCode.TECHNICAL_MANAGER)
        || workOrders.stream().noneMatch(order -> order.getResponsibleId().equals(actor.getId()))) {
      throw new BusinessRuleViolationException(
          "Solo el administrador o el responsable técnico asignado puede realizar esta acción.");
    }
  }

  private ApplicationUser currentUser(String email) {
    ApplicationUser user =
        userRepository
            .findByEmailIgnoreCase(email)
            .orElseThrow(
                () -> new ResourceNotFoundException("el usuario autenticado", UUID.randomUUID()));
    if (!user.isActive()) {
      throw new BusinessRuleViolationException("El usuario autenticado se encuentra desactivado.");
    }
    return user;
  }

  private boolean hasRole(ApplicationUser user, RoleCode role) {
    return user.getRoles().stream().anyMatch(item -> item.getCode() == role);
  }
}
