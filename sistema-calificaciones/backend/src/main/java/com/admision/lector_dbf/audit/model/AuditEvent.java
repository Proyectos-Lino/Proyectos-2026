package com.admision.lector_dbf.audit.model;

import java.time.OffsetDateTime;

public record AuditEvent(long id, Long userId, String userName, String action, String entity, Long entityId,
                         String details, OffsetDateTime occurredAt,Long sheetId) {}
