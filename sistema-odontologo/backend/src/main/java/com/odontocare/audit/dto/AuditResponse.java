package com.odontocare.audit.dto;

import java.time.Instant;
import java.util.UUID;

public record AuditResponse(
    UUID id,
    UUID actorId,
    String actorName,
    String action,
    String entityType,
    String entityId,
    String summary,
    String requestId,
    Instant occurredAt) {}
