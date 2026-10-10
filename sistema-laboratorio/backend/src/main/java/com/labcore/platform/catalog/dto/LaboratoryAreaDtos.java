package com.labcore.platform.catalog;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;

public final class LaboratoryAreaDtos {

  private LaboratoryAreaDtos() {}

  public record CreateRequest(
      @NotBlank @Size(max = 30) String code, @NotBlank @Size(max = 150) String name) {}

  public record UpdateRequest(
      @NotBlank @Size(max = 30) String code, @NotBlank @Size(max = 150) String name) {}

  public record UpdateStatusRequest(@NotNull Boolean active) {}

  public record Response(UUID id, String code, String name, boolean active, Instant createdAt) {}

  public record Option(UUID id, String code, String name) {}
}
