package com.labcore.platform.workorders;

import com.labcore.platform.orders.ServiceOrderStatus;
import com.labcore.platform.organization.OrganizationDtos;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class WorkOrderDtos {
  private WorkOrderDtos() {}

  public record AnalystAssignmentRequest(
      @NotNull UUID sourceSampleTestId, @NotNull UUID analystId) {}

  public record SampleClassificationRequest(
      @NotNull UUID sourceSampleId, @NotNull UUID technicalSampleTypeId) {}

  public record CreateWorkOrderRequest(
      @NotNull UUID serviceOrderId,
      @NotNull UUID laboratoryAreaId,
      @NotNull UUID responsibleId,
      @NotNull @Future Instant laboratoryDueAt,
      @NotEmpty @Valid List<SampleClassificationRequest> sampleClassifications,
      @NotEmpty @Valid List<AnalystAssignmentRequest> assignments) {}

  public record CancelWorkOrderRequest(@NotBlank String reason) {}

  public record WorkOrderTestResponse(
      UUID id,
      UUID sourceSampleTestId,
      UUID testDefinitionId,
      String testName,
      UUID testMethodId,
      String testMethodName,
      UUID assignedAnalystId,
      String assignedAnalystName,
      WorkOrderTestStatus status) {}

  public record WorkOrderSampleResponse(
      UUID id,
      UUID technicalSampleId,
      String laboratoryCode,
      UUID technicalSampleTypeId,
      String technicalSampleTypeName,
      List<WorkOrderTestResponse> tests) {}

  public record WorkOrderSummary(
      UUID id,
      String code,
      UUID serviceOrderId,
      String serviceOrderCode,
      UUID laboratoryAreaId,
      String laboratoryAreaName,
      UUID responsibleId,
      String responsibleName,
      Instant laboratoryDueAt,
      WorkOrderStatus status,
      Instant createdAt,
      int sampleCount) {}

  public record WorkOrderDetail(
      UUID id,
      String code,
      UUID serviceOrderId,
      String serviceOrderCode,
      UUID laboratoryAreaId,
      String laboratoryAreaName,
      UUID responsibleId,
      String responsibleName,
      Instant laboratoryDueAt,
      WorkOrderStatus status,
      ServiceOrderStatus serviceOrderStatus,
      String serviceOrderCorrectionReason,
      String serviceOrderCorrectionChangeNote,
      String serviceOrderCorrectionImpactSummary,
      boolean serviceOrderCorrectionRequiresTechnicalWork,
      Instant cancelledAt,
      String cancellationReason,
      Instant closedAt,
      List<WorkOrderSampleResponse> samples) {}

  public record EligibleServiceOrder(
      UUID id,
      String code,
      Instant plannedDeliveryAt,
      int pendingTestCount,
      int pendingSampleCount) {}

  public record EligibleServiceOrderDetail(
      UUID id,
      String code,
      Instant plannedDeliveryAt,
      List<EligibleLaboratoryArea> laboratoryAreas,
      List<EligibleSample> samples) {}

  public record EligibleLaboratoryArea(UUID id, String name, int pendingTestCount) {}

  public record EligibleSample(
      UUID sourceSampleId,
      int commercialSequence,
      UUID declaredSampleTypeId,
      String declaredSampleTypeName,
      UUID technicalSampleTypeId,
      String technicalSampleTypeName,
      String laboratoryCode,
      List<EligibleTest> pendingTests) {}

  public record EligibleTest(
      UUID sourceSampleTestId,
      UUID laboratoryAreaId,
      String laboratoryAreaName,
      String serviceName,
      UUID testDefinitionId,
      String testName,
      UUID testMethodId,
      String testMethodName) {}

  public record WorkOrderHistoryEntry(
      UUID id,
      WorkOrderHistoryAction action,
      WorkOrderStatus previousStatus,
      WorkOrderStatus newStatus,
      String changeNote,
      String performedBy,
      Instant occurredAt) {
    public static WorkOrderHistoryEntry from(WorkOrderHistory history) {
      return new WorkOrderHistoryEntry(
          history.getId(),
          history.getAction(),
          history.getPreviousStatus(),
          history.getNewStatus(),
          history.getChangeNote(),
          history.getPerformedBy(),
          history.getOccurredAt());
    }
  }

  public record WorkOrderDocumentResponse(
      UUID id,
      int version,
      Instant generatedAt,
      OrganizationDtos.DocumentIdentity organization,
      String code,
      String serviceOrderCode,
      String laboratoryAreaName,
      String responsibleName,
      Instant laboratoryDueAt,
      List<DocumentSample> samples) {}

  public record DocumentSample(
      String laboratoryCode, String sampleTypeName, List<DocumentTest> tests) {}

  public record DocumentTest(String testName, String methodName, String analystName) {}
}
