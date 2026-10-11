package com.labcore.platform.orders;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
    name = "service_order_document",
    uniqueConstraints =
        @UniqueConstraint(
            name = "uq_service_order_document_version",
            columnNames = {"service_order_id", "version"}))
public class ServiceOrderDocument {

  @Id private UUID id = UUID.randomUUID();

  @Column(name = "service_order_id", nullable = false)
  private UUID serviceOrderId;

  @Column(nullable = false)
  private int version;

  @Column(name = "snapshot_content", nullable = false, columnDefinition = "TEXT")
  private String snapshotContent;

  @Column(name = "generated_at", nullable = false)
  private Instant generatedAt = Instant.now();

  @Column(name = "generated_by", nullable = false)
  private String generatedBy;

  protected ServiceOrderDocument() {}

  public ServiceOrderDocument(
      UUID serviceOrderId, int version, String snapshotContent, String generatedBy) {
    this.serviceOrderId = serviceOrderId;
    this.version = version;
    this.snapshotContent = snapshotContent;
    this.generatedBy = generatedBy;
  }

  public UUID getId() {
    return id;
  }

  public int getVersion() {
    return version;
  }

  public String getSnapshotContent() {
    return snapshotContent;
  }

  public Instant getGeneratedAt() {
    return generatedAt;
  }
}
