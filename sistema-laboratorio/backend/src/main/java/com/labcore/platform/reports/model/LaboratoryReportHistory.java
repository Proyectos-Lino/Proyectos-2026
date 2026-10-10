package com.labcore.platform.reports;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "laboratory_report_history")
public class LaboratoryReportHistory {
  @Id private UUID id = UUID.randomUUID();

  @Column(name = "laboratory_report_id", nullable = false)
  private UUID laboratoryReportId;

  @Enumerated(EnumType.STRING)
  private LaboratoryReportHistoryAction action;

  @Enumerated(EnumType.STRING)
  private LaboratoryReportStatus previousStatus;

  @Enumerated(EnumType.STRING)
  private LaboratoryReportStatus newStatus;

  @Column(length = 1000)
  private String changeNote;

  @Column(nullable = false, length = 180)
  private String performedBy;

  private Instant occurredAt = Instant.now();

  protected LaboratoryReportHistory() {}

  public LaboratoryReportHistory(
      UUID laboratoryReportId,
      LaboratoryReportHistoryAction action,
      LaboratoryReportStatus previousStatus,
      LaboratoryReportStatus newStatus,
      String changeNote,
      String performedBy) {
    this.laboratoryReportId = laboratoryReportId;
    this.action = action;
    this.previousStatus = previousStatus;
    this.newStatus = newStatus;
    this.changeNote = changeNote;
    this.performedBy = performedBy;
  }

  public UUID getId() {
    return id;
  }

  public LaboratoryReportHistoryAction getAction() {
    return action;
  }

  public LaboratoryReportStatus getPreviousStatus() {
    return previousStatus;
  }

  public LaboratoryReportStatus getNewStatus() {
    return newStatus;
  }

  public String getChangeNote() {
    return changeNote;
  }

  public String getPerformedBy() {
    return performedBy;
  }

  public Instant getOccurredAt() {
    return occurredAt;
  }
}
