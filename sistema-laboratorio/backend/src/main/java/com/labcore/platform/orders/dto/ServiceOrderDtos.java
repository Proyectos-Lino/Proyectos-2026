package com.labcore.platform.orders;

import com.labcore.platform.organization.OrganizationDtos;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class ServiceOrderDtos {

  private ServiceOrderDtos() {}

  public record CreateServiceOrderRequest(
      @NotNull UUID clientId,
      @NotNull UUID laboratoryAreaId,
      @Size(max = 200) String reportName,
      @Email @Size(max = 180) String reportEmail,
      @Size(max = 300) String reportAddress,
      @NotNull DeliveryMethod deliveryMethod,
      @Size(max = 160) String pickupPerson,
      @NotNull Instant plannedDeliveryAt,
      @Size(max = 80) String proformaNumber,
      @Size(max = 80) String technicalProposalNumber,
      @Min(0) @Max(3650) Integer developmentDays,
      boolean outsourced,
      @NotNull PostAnalysisCustody postAnalysisCustody,
      @Size(max = 5000) String generalObservations,
      @NotEmpty List<@Valid SampleRequest> samples) {}

  public record UpdateCommercialDetailsRequest(
      UUID clientId,
      @Size(max = 200) String reportName,
      @Email @Size(max = 180) String reportEmail,
      @Size(max = 300) String reportAddress,
      @NotNull DeliveryMethod deliveryMethod,
      @Size(max = 160) String pickupPerson,
      @NotNull Instant plannedDeliveryAt,
      @Size(max = 80) String proformaNumber,
      @Size(max = 80) String technicalProposalNumber,
      @Min(0) @Max(3650) Integer developmentDays,
      boolean outsourced,
      @NotNull PostAnalysisCustody postAnalysisCustody,
      @Size(max = 5000) String generalObservations) {}

  public record CancelServiceOrderRequest(@NotBlank @Size(max = 1000) String reason) {}

  public record RequestCommercialCorrectionRequest(@NotBlank @Size(max = 1000) String reason) {}

  public record SubmitCommercialCorrectionRequest(@NotBlank @Size(max = 1000) String changeNote) {}

  public record SampleRequest(
      UUID sourceSampleId,
      @Size(max = 120) String clientIdentification,
      UUID producerId,
      @NotBlank @Size(max = 160) String productName,
      @Size(max = 160) String varietyName,
      @NotNull UUID sampleTypeId,
      @Size(max = 5000) String physicalDescription,
      @Size(max = 250) String samplingLocation,
      @Size(max = 250) String origin,
      @Size(max = 160) String samplerName,
      Instant sampledAt,
      @DecimalMin(value = "0.0001", inclusive = true) BigDecimal quantity,
      UUID measurementUnitId,
      @Size(max = 160) String containerDescription,
      @Size(max = 100) String batchNumber,
      Integer productionYear,
      @Size(max = 120) String brand,
      @Size(max = 120) String originCertificate,
      @Size(max = 120) String sanitaryRegistration,
      @Size(max = 100) String cropAge,
      Integer carrierCount,
      Integer budCount,
      boolean counterSampleInCustody,
      @Size(max = 5000) String observations,
      @NotEmpty List<@Valid RequestedTestRequest> requestedTests) {}

  public record UpdateOrderSamplesRequest(
      @NotEmpty List<@Valid SampleRequest> samples,
      boolean confirmResultInvalidation,
      @Min(0) Integer expectedAffectedResultCount,
      @Size(max = 1000) String changeReason) {}

  public record RequestedTestRequest(
      UUID sourceRequestedTestId,
      @NotNull UUID serviceId,
      @NotNull UUID testDefinitionId,
      @NotNull UUID testMethodId) {}

  public record CorrectionImpactItem(
      UUID sourceSampleId,
      UUID sourceRequestedTestId,
      String sampleLabel,
      String testName,
      String reason,
      int resultCount) {}

  public record CorrectionImpactResponse(
      boolean structuralChange,
      boolean requiresResultInvalidation,
      int affectedWorkOrderCount,
      int affectedResultCount,
      List<CorrectionImpactItem> affectedItems,
      String warningMessage) {}

  public record ServiceOrderSummary(
      UUID id,
      String code,
      String clientName,
      String laboratoryAreaName,
      ServiceOrderStatus status,
      Instant plannedDeliveryAt,
      Instant createdAt,
      String proformaNumber,
      boolean outsourced,
      int sampleCount) {}

  public record ProducerSampleHistory(
      UUID orderId,
      String orderCode,
      ServiceOrderStatus orderStatus,
      Instant orderCreatedAt,
      UUID sampleId,
      int commercialSequence,
      String clientIdentification,
      String productName,
      String varietyName) {}

  public record RequestedTestResponse(
      UUID id,
      UUID serviceId,
      UUID testDefinitionId,
      UUID testMethodId,
      RequestedTestStatus status) {}

  public record TechnicalProgress(
      int totalRequestedTests,
      int assignedTests,
      int validatedTests,
      int pendingAssignmentTests,
      int pendingValidationTests,
      int totalWorkOrders,
      int openWorkOrders,
      boolean canFinalize,
      List<String> blockingReasons) {}

  public record TechnicalIdentificationResponse(
      UUID id,
      UUID technicalSampleTypeId,
      String technicalSampleTypeName,
      String laboratoryCode,
      String classifiedBy,
      Instant classifiedAt) {}

  public record SampleResponse(
      UUID id,
      int commercialSequence,
      String clientIdentification,
      UUID producerId,
      String producerName,
      String producerDocument,
      String productName,
      String varietyName,
      UUID sampleTypeId,
      String physicalDescription,
      String samplingLocation,
      String origin,
      String samplerName,
      Instant sampledAt,
      BigDecimal quantity,
      UUID measurementUnitId,
      String containerDescription,
      String batchNumber,
      Integer productionYear,
      String brand,
      String originCertificate,
      String sanitaryRegistration,
      String cropAge,
      Integer carrierCount,
      Integer budCount,
      boolean counterSampleInCustody,
      String observations,
      TechnicalIdentificationResponse technicalIdentification,
      List<RequestedTestResponse> requestedTests) {}

  public record ServiceOrderDetail(
      UUID id,
      String code,
      UUID clientId,
      String clientName,
      String clientDocument,
      UUID laboratoryAreaId,
      String laboratoryAreaName,
      ServiceOrderStatus status,
      String reportName,
      String reportEmail,
      String reportAddress,
      DeliveryMethod deliveryMethod,
      String pickupPerson,
      Instant plannedDeliveryAt,
      String proformaNumber,
      String technicalProposalNumber,
      Integer developmentDays,
      boolean outsourced,
      PostAnalysisCustody postAnalysisCustody,
      String generalObservations,
      Instant confirmedAt,
      Instant finalizedAt,
      Instant cancelledAt,
      String cancellationReason,
      ServiceOrderStatus statusBeforeCorrection,
      String correctionReason,
      String correctionRequestedBy,
      Instant correctionRequestedAt,
      String correctionSubmittedBy,
      Instant correctionSubmittedAt,
      String correctionChangeNote,
      String correctionReviewedBy,
      Instant correctionReviewedAt,
      String correctionImpactSummary,
      boolean correctionRequiresTechnicalWork,
      Instant createdAt,
      Instant updatedAt,
      List<SampleResponse> samples,
      TechnicalProgress technicalProgress) {}

  public record ServiceOrderHistoryEntry(
      UUID id,
      ServiceOrderHistoryAction action,
      ServiceOrderStatus previousStatus,
      ServiceOrderStatus newStatus,
      String changeNote,
      String performedBy,
      Instant occurredAt) {
    public static ServiceOrderHistoryEntry from(ServiceOrderHistory history) {
      return new ServiceOrderHistoryEntry(
          history.getId(),
          history.getAction(),
          history.getPreviousStatus(),
          history.getNewStatus(),
          history.getChangeNote(),
          history.getPerformedBy(),
          history.getOccurredAt());
    }
  }

  public record ServiceOrderDocumentResponse(
      UUID id,
      int version,
      Instant generatedAt,
      OrganizationDtos.DocumentIdentity organization,
      String code,
      String clientName,
      String clientDocument,
      String laboratoryAreaName,
      String reportName,
      String reportEmail,
      String reportAddress,
      DeliveryMethod deliveryMethod,
      String pickupPerson,
      Instant plannedDeliveryAt,
      String proformaNumber,
      String technicalProposalNumber,
      Integer developmentDays,
      boolean outsourced,
      PostAnalysisCustody postAnalysisCustody,
      String generalObservations,
      List<DocumentSample> samples) {}

  public record DocumentSample(
      int commercialSequence,
      String clientIdentification,
      String producerName,
      String producerDocument,
      String productName,
      String varietyName,
      String sampleTypeName,
      String physicalDescription,
      String samplingLocation,
      String origin,
      String samplerName,
      Instant sampledAt,
      BigDecimal quantity,
      String measurementUnitName,
      String containerDescription,
      String batchNumber,
      Integer productionYear,
      String brand,
      String originCertificate,
      String sanitaryRegistration,
      String cropAge,
      Integer carrierCount,
      Integer budCount,
      boolean counterSampleInCustody,
      String observations,
      List<DocumentTest> requestedTests) {}

  public record DocumentTest(String serviceName, String testName, String methodName) {}
}
