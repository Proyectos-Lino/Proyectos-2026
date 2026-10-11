package com.labcore.platform.audit;

import java.time.Instant;
import java.util.UUID;

public final class AuditLogDtos {
  private AuditLogDtos() {}

  public record Entry(
      UUID id,
      String module,
      UUID resourceId,
      String resourceCode,
      String action,
      String description,
      String actor,
      Instant occurredAt) {}
}
