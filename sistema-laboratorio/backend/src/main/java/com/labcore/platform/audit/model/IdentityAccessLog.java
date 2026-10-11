package com.labcore.platform.audit;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "identity_access_log")
public class IdentityAccessLog {

  @Id private UUID id = UUID.randomUUID();

  @Column(name = "resource_type", nullable = false)
  private String resourceType;

  @Column(name = "resource_id", nullable = false)
  private UUID resourceId;

  @Column(name = "resource_code", nullable = false)
  private String resourceCode;

  @Column(nullable = false)
  private String action;

  @Column(nullable = false)
  private String reason;

  @Column(nullable = false)
  private String actor;

  @Column(name = "accessed_at", nullable = false)
  private Instant accessedAt = Instant.now();

  protected IdentityAccessLog() {}

  public IdentityAccessLog(
      String resourceType,
      UUID resourceId,
      String resourceCode,
      String action,
      String reason,
      String actor) {
    this.resourceType = resourceType;
    this.resourceId = resourceId;
    this.resourceCode = resourceCode;
    this.action = action;
    this.reason = reason;
    this.actor = actor;
  }
}
