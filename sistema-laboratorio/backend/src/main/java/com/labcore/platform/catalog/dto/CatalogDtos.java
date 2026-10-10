package com.labcore.platform.catalog;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

public final class CatalogDtos {

  private CatalogDtos() {}

  public record AreaRequest(
      @NotBlank @Size(max = 30) String code, @NotBlank @Size(max = 150) String name) {}

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

  public record TestMethodRequest(
      @NotNull UUID testDefinitionId,
      @NotBlank @Size(max = 50) String code,
      @NotBlank @Size(max = 200) String name,
      @Size(max = 60) String versionLabel) {}

  public record ServiceRequest(
      @NotBlank @Size(max = 40) String code,
      @NotBlank @Size(max = 200) String name,
      @NotNull UUID laboratoryAreaId,
      @NotEmpty List<UUID> testIds) {}

  public record Item(UUID id, String code, String name) {}

  public record SampleTypeItem(
      UUID id, String code, String name, String laboratoryCodePrefix, ResultMode resultMode) {}

  public record UnitItem(UUID id, String code, String name, String symbol) {}

  public record TestItem(
      UUID id, String code, String name, ResultDataType resultType, UUID resultUnitId) {}

  public record ServiceItem(UUID id, String code, String name, UUID laboratoryAreaId) {}

  public record TestMethodItem(
      UUID id, UUID testDefinitionId, String code, String name, String versionLabel) {}

  public record ServiceTestItem(
      UUID serviceId, UUID testDefinitionId, int displayOrder, boolean required) {}

  public record ProductItem(UUID id, String code, String name) {}

  public record VarietyItem(UUID id, UUID productId, String code, String name) {}

  public record CatalogSnapshot(
      List<Item> areas,
      List<SampleTypeItem> sampleTypes,
      List<UnitItem> units,
      List<TestItem> tests,
      List<ServiceItem> services,
      List<TestMethodItem> methods,
      List<ServiceTestItem> serviceTests,
      List<ProductItem> products,
      List<VarietyItem> varieties) {}
}
