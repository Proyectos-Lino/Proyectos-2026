package com.labcore.platform.reports;

import com.labcore.platform.catalog.ResultFieldKind;
import com.labcore.platform.organization.OrganizationDtos;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class LaboratoryReportDtos {
  private LaboratoryReportDtos() {}

  public record CancelRequest(@NotBlank @Size(max = 1000) String reason) {}

  public record PreparationCandidate(UUID id, String code, Instant plannedDeliveryAt) {}

  public record PdfFile(String reportCode, byte[] content) {}

  public record Summary(
      UUID id,
      String code,
      UUID serviceOrderId,
      String serviceOrderCode,
      String clientName,
      int version,
      LaboratoryReportStatus status,
      Instant createdAt,
      Instant issuedAt) {}

  public record Detail(
      UUID id,
      String code,
      UUID serviceOrderId,
      int version,
      LaboratoryReportStatus status,
      String checksum,
      String createdBy,
      Instant createdAt,
      String issuedBy,
      Instant issuedAt,
      String cancelledBy,
      Instant cancelledAt,
      String cancellationReason,
      Snapshot snapshot) {}

  public record HistoryEntry(
      UUID id,
      LaboratoryReportHistoryAction action,
      LaboratoryReportStatus previousStatus,
      LaboratoryReportStatus newStatus,
      String changeNote,
      String performedBy,
      Instant occurredAt) {}

  public record Snapshot(
      String reportCode,
      int version,
      Instant generatedAt,
      OrganizationDtos.DocumentIdentity organization,
      String serviceOrderCode,
      String clientName,
      String clientDocument,
      String reportName,
      String reportEmail,
      String reportAddress,
      String laboratoryAreaName,
      String generalObservations,
      List<SampleResult> samples) {}

  public record SampleResult(
      int sequence,
      String laboratoryCode,
      String clientIdentification,
      String producerName,
      String producerDocument,
      String productName,
      String varietyName,
      String sampleTypeName,
      String physicalDescription,
      String origin,
      String samplingLocation,
      Instant sampledAt,
      BigDecimal quantity,
      String quantityUnit,
      String containerDescription,
      String batchNumber,
      String brand,
      String observations,
      List<TestResult> tests) {}

  public record TestResult(
      String testName,
      String methodName,
      String analystName,
      String analystObservation,
      String validatorName,
      Instant validatedAt,
      List<Value> values,
      Matrix matrix) {}

  public record Value(
      String label,
      int repetition,
      String textValue,
      BigDecimal numericValue,
      String unitSymbol,
      ResultFieldKind fieldKind,
      String formulaUsed,
      String calculationInputs) {}

  public record Matrix(
      int rowCount,
      int columnCount,
      List<MatrixColumn> columns,
      List<MatrixCell> cells,
      List<MatrixCategorySummary> categorySummary) {}

  public record MatrixCategorySummary(String category, long count, BigDecimal percentage) {}

  public record MatrixColumn(
      int index, String label, BigDecimal distance, BigDecimal diameter, String shape) {}

  public record MatrixCell(
      int row, int column, String textValue, BigDecimal numericValue, String category) {}
}
