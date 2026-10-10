package com.labcore.platform.reports;

import com.labcore.platform.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "laboratory_report")
public class LaboratoryReport extends BaseEntity {
  @Id private UUID id = UUID.randomUUID();
  private String code;

  @Column(name = "service_order_id", nullable = false)
  private UUID serviceOrderId;

  @Column(name = "report_version", nullable = false)
  private int reportVersion;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private LaboratoryReportStatus status = LaboratoryReportStatus.DRAFT;

  @Column(name = "snapshot_json", nullable = false, columnDefinition = "text")
  private String snapshotJson;

  @Column(name = "pdf_content", nullable = false, columnDefinition = "bytea")
  private byte[] pdfContent;

  @Column(name = "content_checksum", nullable = false, length = 64)
  private String contentChecksum;

  @Column(name = "created_by", nullable = false, length = 180)
  private String createdBy;

  private String issuedBy;
  private Instant issuedAt;
  private String cancelledBy;
  private Instant cancelledAt;

  @Column(length = 1000)
  private String cancellationReason;

  protected LaboratoryReport() {}

  public LaboratoryReport(
      String code,
      UUID serviceOrderId,
      int reportVersion,
      String snapshotJson,
      byte[] pdfContent,
      String contentChecksum,
      String createdBy) {
    this.code = code;
    this.serviceOrderId = serviceOrderId;
    this.reportVersion = reportVersion;
    this.snapshotJson = snapshotJson;
    this.pdfContent = pdfContent.clone();
    this.contentChecksum = contentChecksum;
    this.createdBy = createdBy;
  }

  public UUID getId() {
    return id;
  }

  public String getCode() {
    return code;
  }

  public UUID getServiceOrderId() {
    return serviceOrderId;
  }

  public int getReportVersion() {
    return reportVersion;
  }

  public LaboratoryReportStatus getStatus() {
    return status;
  }

  public String getSnapshotJson() {
    return snapshotJson;
  }

  public byte[] getPdfContent() {
    return pdfContent.clone();
  }

  public String getContentChecksum() {
    return contentChecksum;
  }

  public String getCreatedBy() {
    return createdBy;
  }

  public String getIssuedBy() {
    return issuedBy;
  }

  public Instant getIssuedAt() {
    return issuedAt;
  }

  public String getCancelledBy() {
    return cancelledBy;
  }

  public Instant getCancelledAt() {
    return cancelledAt;
  }

  public String getCancellationReason() {
    return cancellationReason;
  }

  public void issue(String actor) {
    status = LaboratoryReportStatus.ISSUED;
    issuedBy = actor;
    issuedAt = Instant.now();
  }

  public void replacePdf(byte[] content) {
    pdfContent = content.clone();
  }

  public void cancel(String actor, String reason) {
    status = LaboratoryReportStatus.CANCELLED;
    cancelledBy = actor;
    cancelledAt = Instant.now();
    cancellationReason = reason;
  }
}
