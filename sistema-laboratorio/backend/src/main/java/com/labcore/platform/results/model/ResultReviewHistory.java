package com.labcore.platform.results;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "result_review_history")
public class ResultReviewHistory {
  @Id private UUID id = UUID.randomUUID();

  @Column(name = "result_entry_id")
  private UUID resultEntryId;

  @Enumerated(EnumType.STRING)
  private ResultReviewAction action;

  @Enumerated(EnumType.STRING)
  private ResultEntryStatus previousStatus;

  @Enumerated(EnumType.STRING)
  private ResultEntryStatus newStatus;

  private int resultRevision;
  private String reason;
  private String comment;

  @Column(name = "performed_by")
  private UUID performedBy;

  private String performedByRole;
  private Instant performedAt;

  protected ResultReviewHistory() {}

  public ResultReviewHistory(
      UUID resultEntryId,
      ResultReviewAction action,
      ResultEntryStatus previousStatus,
      ResultEntryStatus newStatus,
      int resultRevision,
      String reason,
      String comment,
      UUID performedBy,
      String performedByRole) {
    this.resultEntryId = resultEntryId;
    this.action = action;
    this.previousStatus = previousStatus;
    this.newStatus = newStatus;
    this.resultRevision = resultRevision;
    this.reason = reason;
    this.comment = comment;
    this.performedBy = performedBy;
    this.performedByRole = performedByRole;
    this.performedAt = Instant.now();
  }

  public UUID getId() {
    return id;
  }

  public ResultReviewAction getAction() {
    return action;
  }

  public ResultEntryStatus getPreviousStatus() {
    return previousStatus;
  }

  public ResultEntryStatus getNewStatus() {
    return newStatus;
  }

  public int getResultRevision() {
    return resultRevision;
  }

  public String getReason() {
    return reason;
  }

  public String getComment() {
    return comment;
  }

  public UUID getPerformedBy() {
    return performedBy;
  }

  public String getPerformedByRole() {
    return performedByRole;
  }

  public Instant getPerformedAt() {
    return performedAt;
  }
}
