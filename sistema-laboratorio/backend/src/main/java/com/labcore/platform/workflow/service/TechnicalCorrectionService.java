package com.labcore.platform.workflow;

import com.labcore.platform.common.exception.BusinessRuleViolationException;
import com.labcore.platform.orders.*;
import com.labcore.platform.reports.*;
import com.labcore.platform.results.*;
import com.labcore.platform.workorders.*;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class TechnicalCorrectionService {
  private final WorkOrderService workOrders;
  private final WorkOrderSampleRepository samples;
  private final WorkOrderTestRepository tests;
  private final ResultEntryRepository entries;
  private final ResultService results;
  private final ServiceOrderService orders;
  private final ServiceOrderHistoryRepository orderHistory;
  private final LaboratoryReportService reports;
  private final CorrectionAuthority authority;
  private final OperationVersionService versions;
  private final LaboratoryReportRepository reportRepository;
  private final WorkOrderRepository workOrderRepository;

  public TechnicalCorrectionService(
      WorkOrderService workOrders,
      WorkOrderSampleRepository samples,
      WorkOrderTestRepository tests,
      ResultEntryRepository entries,
      ResultService results,
      ServiceOrderService orders,
      ServiceOrderHistoryRepository orderHistory,
      LaboratoryReportService reports,
      CorrectionAuthority authority,
      OperationVersionService versions,
      LaboratoryReportRepository reportRepository,
      WorkOrderRepository workOrderRepository) {
    this.workOrders = workOrders;
    this.samples = samples;
    this.tests = tests;
    this.entries = entries;
    this.results = results;
    this.orders = orders;
    this.orderHistory = orderHistory;
    this.reports = reports;
    this.authority = authority;
    this.versions = versions;
    this.reportRepository = reportRepository;
    this.workOrderRepository = workOrderRepository;
  }

  public WorkOrderDtos.WorkOrderDetail returnWorkOrder(UUID id, String reason, String actor) {
    WorkOrder order = workOrders.getRequired(id);
    authority.requireManager(order, actor);
    ensureAvailable(order);
    WorkOrderStatus previous = order.getStatus();
    WorkOrderStatus target =
        switch (previous) {
          case CLOSED -> WorkOrderStatus.VALIDATED;
          case VALIDATED -> WorkOrderStatus.RESULTS_RECORDED;
          case RESULTS_RECORDED -> WorkOrderStatus.IN_PROGRESS;
          case IN_PROGRESS -> WorkOrderStatus.PENDING;
          default ->
              throw new BusinessRuleViolationException(
                  "Esta orden no tiene una etapa técnica anterior. Puede solicitar una corrección comercial.");
        };
    workOrders.captureVersion(id, "BEFORE_CORRECTION", reason, actor);
    reopenParent(order, reason, actor);
    for (WorkOrderTest test : activeTests(id)) {
      var entry = entries.findByWorkOrderTestId(test.getId()).orElse(null);
      if (entry == null
          || entry.getStatus() == ResultEntryStatus.DRAFT
          || entry.getStatus() == ResultEntryStatus.CORRECTION_REQUESTED
          || entry.getStatus() == ResultEntryStatus.INVALIDATED) continue;
      if (previous == WorkOrderStatus.VALIDATED
          || previous == WorkOrderStatus.RESULTS_RECORDED
          || previous == WorkOrderStatus.IN_PROGRESS) {
        results.returnForCorrection(
            test.getId(), reason, previous == WorkOrderStatus.VALIDATED, actor);
      }
    }
    order.returnTo(target);
    workOrders.registerReturn(
        order,
        previous,
        "Devolución a "
            + com.labcore.platform.common.i18n.SystemLabel.forValue(target)
            + ". Motivo: "
            + reason,
        actor);
    return workOrders.getDetail(id, actor);
  }

  public ResultDtos.ResultResponse returnResult(UUID testId, String reason, String actor) {
    WorkOrderTest test = workOrders.getRequiredTest(testId);
    WorkOrderSample sample = samples.findById(test.getWorkOrderSampleId()).orElseThrow();
    WorkOrder order = workOrders.getRequired(sample.getWorkOrderId());
    authority.requireManager(order, actor);
    ensureAvailable(order);
    workOrders.captureVersion(order.getId(), "BEFORE_CORRECTION", reason, actor);
    reopenParent(order, reason, actor);
    if (order.getStatus() == WorkOrderStatus.CLOSED) order.returnTo(WorkOrderStatus.VALIDATED);
    ResultEntry entry =
        entries
            .findByWorkOrderTestId(testId)
            .orElseThrow(
                () -> new BusinessRuleViolationException("No hay un resultado que devolver."));
    results.returnForCorrection(
        testId, reason, entry.getStatus() == ResultEntryStatus.VALIDATED, actor);
    return results.getByWorkOrderTest(testId, actor);
  }

  private void reopenParent(WorkOrder order, String reason, String actor) {
    ServiceOrder source = orders.getRequired(order.getServiceOrderId());
    if (source.getStatus() != ServiceOrderStatus.IN_PROGRESS
        && source.getStatus() != ServiceOrderStatus.FINALIZED)
      throw new BusinessRuleViolationException(
          "La orden de servicio no está disponible para corrección técnica.");
    versions.capture(
        OperationType.SERVICE_ORDER,
        source.getId(),
        "BEFORE_CORRECTION",
        reason,
        () -> orders.getDetail(source.getId()),
        actor);
    if (source.getStatus() == ServiceOrderStatus.FINALIZED) {
      source.reopenTechnicalProcess();
      orderHistory.save(
          new ServiceOrderHistory(
              source.getId(),
              ServiceOrderHistoryAction.TECHNICAL_PROCESS_REOPENED,
              ServiceOrderStatus.FINALIZED,
              source.getStatus(),
              reason,
              actor));
      versions.capture(
          OperationType.SERVICE_ORDER,
          source.getId(),
          "TECHNICAL_PROCESS_REOPENED",
          reason,
          () -> orders.getDetail(source.getId()),
          actor);
    }
    reports.cancelOpenReportsForCommercialCorrection(
        source.getId(), "Corrección técnica: " + reason, actor);
  }

  private List<WorkOrderTest> activeTests(UUID id) {
    return tests
        .findByWorkOrderSampleIdIn(
            samples.findByWorkOrderId(id).stream().map(WorkOrderSample::getId).toList())
        .stream()
        .filter(test -> test.getStatus() != WorkOrderTestStatus.INVALIDATED)
        .toList();
  }

  private void ensureAvailable(WorkOrder order) {
    if (order.getStatus() == WorkOrderStatus.CANCELLED
        || order.getStatus() == WorkOrderStatus.COMMERCIAL_CORRECTION)
      throw new BusinessRuleViolationException(
          "No se puede devolver una orden anulada o en corrección comercial.");
  }

  public void returnReport(UUID reportId, String reason, String actorEmail) {
    var report =
        reportRepository
            .findById(reportId)
            .orElseThrow(
                () ->
                    new com.labcore.platform.common.exception.ResourceNotFoundException(
                        "el informe", reportId));
    var actor = authority.user(actorEmail);
    List<WorkOrder> related =
        workOrderRepository
            .findByServiceOrderIdOrderByCreatedAtAsc(report.getServiceOrderId())
            .stream()
            .filter(order -> order.getStatus() != WorkOrderStatus.CANCELLED)
            .toList();
    if (!authority.hasRole(actor, com.labcore.platform.security.RoleCode.ADMIN)
        && !(authority.hasRole(actor, com.labcore.platform.security.RoleCode.TECHNICAL_MANAGER)
            && !related.isEmpty()
            && related.stream().allMatch(order -> order.getResponsibleId().equals(actor.getId()))))
      throw new org.springframework.security.access.AccessDeniedException(
          "Solo administración o el responsable de las órdenes técnicas del informe puede devolverlo.");
    if (report.getStatus() == LaboratoryReportStatus.ISSUED) {
      reports.returnToDraft(reportId, reason, actorEmail);
      return;
    }
    if (report.getStatus() != LaboratoryReportStatus.DRAFT)
      throw new BusinessRuleViolationException(
          "No se puede devolver una versión de informe ya anulada.");
    for (WorkOrder order : related) {
      if (order.getStatus() == WorkOrderStatus.CLOSED)
        returnWorkOrder(order.getId(), reason, actorEmail);
      if (order.getStatus() == WorkOrderStatus.VALIDATED)
        returnWorkOrder(order.getId(), reason, actorEmail);
    }
  }
}
