package com.labcore.platform.workflow;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.UUID;

public final class OperationVersionDtos {
  private OperationVersionDtos() {}

  public record Version(
      UUID id,
      int revision,
      String action,
      String reason,
      String performedBy,
      String performedByRole,
      Instant capturedAt,
      JsonNode snapshot) {}
}
