package com.labcore.platform.workflow;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class OperationCorrectionDtos {
  private OperationCorrectionDtos() {}

  public record ReturnRequest(@NotBlank @Size(max = 1000) String reason) {}

  public record Assignment(@NotNull UUID testId, @NotNull UUID analystId) {}

  public record CorrectAssignmentRequest(
      @NotBlank @Size(max = 1000) String reason,
      @NotNull Instant laboratoryDueAt,
      @NotEmpty List<@Valid Assignment> assignments) {}
}
