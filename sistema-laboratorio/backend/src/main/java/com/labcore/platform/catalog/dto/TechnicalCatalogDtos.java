package com.labcore.platform.catalog;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
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

public final class TechnicalCatalogDtos {

  private TechnicalCatalogDtos() {}

  public record UnitRequest(
      @NotBlank @Size(max = 20) String code,
      @NotBlank @Size(max = 80) String name,
      @NotBlank @Size(max = 20) String symbol) {}

  public record SampleTypeRequest(
      @NotBlank @Size(max = 30) String code,
      @NotBlank @Size(max = 120) String name,
      @NotBlank @Size(max = 20) String laboratoryCodePrefix,
      @NotNull ResultMode resultMode) {}

  public record TestRequest(
      @NotBlank @Size(max = 40) String code,
      @NotBlank @Size(max = 200) String name,
      @NotNull ResultDataType resultType,
      UUID resultUnitId) {}

  public record MethodRequest(
      @NotNull UUID testDefinitionId,
      @NotBlank @Size(max = 50) String code,
      @NotBlank @Size(max = 200) String name,
      @Size(max = 60) String versionLabel) {}

  public record TemplateFieldRequest(
      @NotBlank @Size(max = 80) String fieldKey,
      @NotBlank @Size(max = 120) String label,
      @NotNull ResultDataType resultType,
      UUID measurementUnitId,
      boolean required,
      @Min(0) int displayOrder,
      @NotNull ResultFieldKind fieldKind,
      boolean readOnly,
      @Size(max = 1000) String formula,
      @NotNull CalculationScope calculationScope,
      @Min(0) @Max(10) Integer decimalPrecision,
      @Size(max = 500) String informationText,
      @Size(max = 500) String defaultValue) {}

  public record TemplateCategoryRequest(
      @NotBlank @Size(max = 50) String code,
      @NotBlank @Size(max = 120) String label,
      @Min(0) int displayOrder) {}

  public record TemplateRequest(
      @NotNull UUID testDefinitionId,
      UUID sampleTypeId,
      @NotBlank @Size(max = 160) String name,
      @Size(max = 500) String description,
      @NotNull ResultMode resultMode,
      @Min(1) int minimumRepetitions,
      Integer maximumRepetitions,
      @Min(1) int defaultRows,
      Integer maximumRows,
      @Min(1) int defaultColumns,
      Integer maximumColumns,
      @NotEmpty List<@Valid TemplateFieldRequest> fields,
      @NotNull List<@Valid TemplateCategoryRequest> categories) {}

  public record StatusRequest(@NotNull Boolean active) {}

  public record UnitResponse(
      UUID id, String code, String name, String symbol, boolean active, Instant createdAt) {}

  public record SampleTypeResponse(
      UUID id,
      String code,
      String name,
      String laboratoryCodePrefix,
      ResultMode resultMode,
      boolean active,
      Instant createdAt) {}

  public record TestResponse(
      UUID id,
      String code,
      String name,
      ResultDataType resultType,
      UUID resultUnitId,
      boolean active,
      Instant createdAt) {}

  public record MethodResponse(
      UUID id,
      UUID testDefinitionId,
      String code,
      String name,
      String versionLabel,
      boolean active,
      Instant createdAt) {}

  public record TemplateFieldResponse(
      UUID id,
      String fieldKey,
      String label,
      ResultDataType resultType,
      UUID measurementUnitId,
      boolean required,
      int displayOrder,
      ResultFieldKind fieldKind,
      boolean readOnly,
      String formula,
      CalculationScope calculationScope,
      Integer decimalPrecision,
      String informationText,
      String defaultValue) {}

  public record FormulaPreviewRequest(
      @NotEmpty List<@Valid TemplateFieldRequest> fields,
      @NotNull Map<String, List<BigDecimal>> sampleValues) {}

  public record FormulaPreviewValue(
      String fieldKey,
      int repetitionIndex,
      String formula,
      Map<String, List<BigDecimal>> inputs,
      BigDecimal value) {}

  public record FormulaPreviewResponse(List<FormulaPreviewValue> values) {}

  public record TemplateCategoryResponse(UUID id, String code, String label, int displayOrder) {}

  public record TemplateResponse(
      UUID id,
      UUID testDefinitionId,
      String testDefinitionName,
      UUID sampleTypeId,
      String sampleTypeName,
      String name,
      String description,
      ResultMode resultMode,
      int minimumRepetitions,
      Integer maximumRepetitions,
      int defaultRows,
      Integer maximumRows,
      int defaultColumns,
      Integer maximumColumns,
      boolean active,
      Instant createdAt,
      List<TemplateFieldResponse> fields,
      List<TemplateCategoryResponse> categories) {}
}
