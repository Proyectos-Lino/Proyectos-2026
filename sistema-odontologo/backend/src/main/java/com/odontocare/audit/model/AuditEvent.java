package com.odontocare.audit.model;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.Immutable;

@Entity
@Immutable
@Table(name = "audit_event")
public class AuditEvent {
  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "actor_id")
  private UUID actorId;

  @Column(name = "actor_name", nullable = false, length = 120)
  private String actorName;

  @Column(nullable = false, length = 40)
  private String action;

  @Column(name = "entity_type", nullable = false, length = 40)
  private String entityType;

  @Column(name = "entity_id", nullable = false, length = 64)
  private String entityId;

  @Column(nullable = false, length = 1000)
  private String summary;

  @Column(name = "request_id", length = 36)
  private String requestId;

  @Column(name = "occurred_at", nullable = false)
  private Instant occurredAt;

  protected AuditEvent() {}

  public AuditEvent(
      UUID actorId,
      String actorName,
      String action,
      String entityType,
      String entityId,
      String summary,
      String requestId) {
    this.actorId = actorId;
    this.actorName = actorName;
    this.action = action;
    this.entityType = entityType;
    this.entityId = entityId;
    this.summary = summary;
    this.requestId = requestId;
    this.occurredAt = Instant.now();
  }

  public UUID getId() {
    return id;
  }

  public UUID getActorId() {
    return actorId;
  }

  public String getActorName() {
    return actorName;
  }

  public String getAction() {
    return action;
  }

  public String getEntityType() {
    return entityType;
  }

  public String getEntityId() {
    return entityId;
  }

  public String getSummary() {
    return summary;
  }

  public String getRequestId() {
    return requestId;
  }

  public Instant getOccurredAt() {
    return occurredAt;
  }
}
