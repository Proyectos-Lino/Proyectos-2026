package com.labcore.platform.workflow;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "operation_version")
public class OperationVersion {
  @Id private UUID id = UUID.randomUUID();

  @Enumerated(EnumType.STRING)
  private OperationType operationType;

  private UUID operationId;
  private int revision;
  private String action;
  private String reason;
  private String performedBy;
  private String performedByRole;
  private Instant capturedAt = Instant.now();

  @Column(columnDefinition = "TEXT")
  private String snapshotContent;

  protected OperationVersion() {}

  public OperationVersion(
      OperationType type,
      UUID operationId,
      int revision,
      String action,
      String reason,
      String actor,
      String role,
      String content) {
    this.operationType = type;
    this.operationId = operationId;
    this.revision = revision;
    this.action = action;
    this.reason = reason;
    this.performedBy = actor;
    this.performedByRole = role;
    this.snapshotContent = content;
  }

  public UUID getId() {
    return id;
  }

  public int getRevision() {
    return revision;
  }

  public String getAction() {
    return action;
  }

  public String getReason() {
    return reason;
  }

  public String getPerformedBy() {
    return performedBy;
  }

  public String getPerformedByRole() {
    return performedByRole;
  }

  public Instant getCapturedAt() {
    return capturedAt;
  }

  public String getSnapshotContent() {
    return snapshotContent;
  }
}
