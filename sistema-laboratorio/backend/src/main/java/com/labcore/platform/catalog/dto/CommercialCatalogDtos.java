package com.labcore.platform.catalog;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class CommercialCatalogDtos {

  private CommercialCatalogDtos() {}

  public record ServiceTestRequest(@NotNull UUID testDefinitionId, boolean required) {}

  public record ServiceRequest(
      @NotBlank @Size(max = 40) String code,
      @NotBlank @Size(max = 200) String name,
      @NotNull UUID laboratoryAreaId,
      @NotEmpty List<@Valid ServiceTestRequest> tests) {}

  public record ProductRequest(
      @NotBlank @Size(max = 40) String code, @NotBlank @Size(max = 160) String name) {}

  public record VarietyRequest(
      @NotNull UUID productId,
      @NotBlank @Size(max = 40) String code,
      @NotBlank @Size(max = 160) String name) {}

  public record StatusRequest(@NotNull Boolean active) {}

  public record ServiceTestResponse(
      UUID testDefinitionId, String testDefinitionName, int displayOrder, boolean required) {}

  public record ServiceResponse(
      UUID id,
      String code,
      String name,
      UUID laboratoryAreaId,
      String laboratoryAreaName,
      boolean active,
      Instant createdAt,
      List<ServiceTestResponse> tests) {}

  public record ProductResponse(
      UUID id, String code, String name, boolean active, Instant createdAt) {}

  public record VarietyResponse(
      UUID id,
      UUID productId,
      String productName,
      String code,
      String name,
      boolean active,
      Instant createdAt) {}
}
