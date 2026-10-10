package com.labcore.platform.results;

import com.labcore.platform.catalog.ResultFieldKind;
import com.labcore.platform.catalog.ResultMode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class ResultDtos {
  private ResultDtos() {}

  public record ValueRequest(
      @NotBlank @Size(max = 80) String fieldKey,
      @NotBlank @Size(max = 120) String label,
      int repetitionIndex,
      @Size(max = 500) String valueText,
      BigDecimal valueNumber,
      UUID measurementUnitId) {}

  public record MatrixColumnRequest(
      int columnIndex,
      @Size(max = 120) String carrierLabel,
      BigDecimal distance,
      BigDecimal diameter,
      @Size(max = 120) String shape) {}

  public record MatrixCellRequest(
      int rowIndex,
      int columnIndex,
      @Size(max = 500) String valueText,
      BigDecimal valueNumber,
      @Size(max = 50) String categoryCode) {}

  public record MatrixRequest(
      @Min(1) int rowCount,
      @Min(1) int columnCount,
      @NotEmpty List<@Valid MatrixColumnRequest> columns,
      @NotEmpty List<@Valid MatrixCellRequest> cells) {}

  public record StartMatrixExecutionRequest(
      @Min(1) int declaredRowCount, @Min(1) int declaredColumnCount) {}

  public record SaveMatrixDraftRequest(
      @NotNull @Valid MatrixRequest matrix,
      @Size(max = 500) String adjustmentReason,
      @Size(max = 5000) String analystObservation) {}

  public record RecordResultRequest(
      @Size(max = 5000) String analystObservation,
      @Size(max = 500) String revisionReason,
      @NotNull @Valid List<ValueRequest> values,
      @Valid MatrixRequest matrix) {}

  public record ValidateResultRequest(@Size(max = 1000) String comment) {}

  public record RejectResultRequest(@NotBlank @Size(max = 500) String reason) {}

  public record CorrectAndValidateResultRequest(
      @NotBlank @Size(max = 500) String reason,
      @Size(max = 1000) String comment,
      @Size(max = 5000) String analystObservation,
      @NotNull @Valid List<ValueRequest> values,
      @Valid MatrixRequest matrix) {}

  public record ValueResponse(
      UUID id,
      String fieldKey,
      String label,
      int position,
      int repetitionIndex,
      String valueText,
      BigDecimal valueNumber,
      UUID measurementUnitId,
      ResultFieldKind fieldKind,
      String formulaUsed,
      String calculationInputs) {}

  public record CalculationSnapshotResponse(
      UUID id,
      String fieldKey,
      int repetitionIndex,
      int resultRevision,
      String formulaUsed,
      String calculationInputs,
      BigDecimal calculatedValue,
      UUID calculatedBy,
      String calculatedByRole,
      Instant calculatedAt) {}

  public record CalculationPreviewRequest(@NotNull @Valid List<ValueRequest> values) {}

  public record CalculatedValueResponse(
      String fieldKey,
      String label,
      int repetitionIndex,
      BigDecimal value,
      UUID measurementUnitId,
      String formula,
      Map<String, List<BigDecimal>> inputs) {}

  public record CalculationPreviewResponse(List<CalculatedValueResponse> values) {}

  public record ValidationResponse(
      UUID validatorId,
      String validatorName,
      String validatorRole,
      int resultRevision,
      Instant validatedAt,
      String comment) {}

  public record CorrectionResponse(
      UUID id,
      UUID resultValueId,
      String fieldLabel,
      String oldValueText,
      BigDecimal oldValueNumber,
      String newValueText,
      BigDecimal newValueNumber,
      String reason,
      UUID changedBy,
      String changedByName,
      String changedByRole,
      Instant changedAt) {}

  public record MatrixColumnResponse(
      UUID id,
      int columnIndex,
      String carrierLabel,
      BigDecimal distance,
      BigDecimal diameter,
      String shape) {}

  public record MatrixCellResponse(
      UUID id,
      int rowIndex,
      int columnIndex,
      String valueText,
      BigDecimal valueNumber,
      String categoryCode) {}

  public record MatrixCategorySummaryResponse(
      String categoryCode, String categoryLabel, long count, BigDecimal percentage) {}

  public record MatrixResponse(
      UUID id,
      int rowCount,
      int columnCount,
      int declaredRowCount,
      int declaredColumnCount,
      MatrixExecutionStatus executionStatus,
      BigDecimal completionPercentage,
      Instant startedAt,
      Instant completedAt,
      Instant lastModifiedAt,
      UUID lastModifiedBy,
      String lastModifiedByName,
      List<MatrixColumnResponse> columns,
      List<MatrixCellResponse> cells,
      List<MatrixCategorySummaryResponse> categorySummary) {}

  public record MatrixCorrectionResponse(
      UUID id,
      String targetType,
      Integer rowIndex,
      Integer columnIndex,
      String fieldName,
      String oldValue,
      String newValue,
      String reason,
      UUID changedBy,
      String changedByName,
      String changedByRole,
      Instant changedAt) {}

  public record ReviewHistoryResponse(
      UUID id,
      ResultReviewAction action,
      ResultEntryStatus previousStatus,
      ResultEntryStatus newStatus,
      int resultRevision,
      String reason,
      String comment,
      UUID performedBy,
      String performedByName,
      String performedByRole,
      Instant performedAt) {}

  public record ResultResponse(
      UUID id,
      UUID workOrderTestId,
      UUID resultTemplateId,
      ResultMode resultMode,
      ResultEntryStatus status,
      int resultRevision,
      String analystObservation,
      UUID recordedBy,
      String recordedByName,
      Instant recordedAt,
      List<ValueResponse> values,
      List<CorrectionResponse> corrections,
      MatrixResponse matrix,
      List<MatrixCorrectionResponse> matrixCorrections,
      List<CalculationSnapshotResponse> calculationSnapshots,
      String correctionRequestReason,
      UUID correctionRequestedBy,
      String correctionRequestedByName,
      String correctionRequestedByRole,
      Instant correctionRequestedAt,
      List<ReviewHistoryResponse> reviewHistory,
      ValidationResponse validation) {}
}
