package com.labcore.platform.workorders;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
    name = "work_order_document",
    uniqueConstraints =
        @UniqueConstraint(
            name = "uq_work_order_document_version",
            columnNames = {"work_order_id", "version"}))
public class WorkOrderDocument {

  @Id private UUID id = UUID.randomUUID();

  @Column(name = "work_order_id", nullable = false)
  private UUID workOrderId;

  @Column(nullable = false)
  private int version;

  @Column(name = "snapshot_content", nullable = false, columnDefinition = "TEXT")
  private String snapshotContent;

  @Column(name = "generated_at", nullable = false)
  private Instant generatedAt = Instant.now();

  @Column(name = "generated_by", nullable = false, length = 180)
  private String generatedBy;

  protected WorkOrderDocument() {}

  public WorkOrderDocument(
      UUID workOrderId, int version, String snapshotContent, String generatedBy) {
    this.workOrderId = workOrderId;
    this.version = version;
    this.snapshotContent = snapshotContent;
    this.generatedBy = generatedBy;
  }

  public UUID getId() {
    return id;
  }

  public UUID getWorkOrderId() {
    return workOrderId;
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
