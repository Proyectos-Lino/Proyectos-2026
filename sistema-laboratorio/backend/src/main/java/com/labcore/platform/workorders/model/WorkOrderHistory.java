package com.labcore.platform.workorders;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "work_order_history")
public class WorkOrderHistory {

  @Id private UUID id = UUID.randomUUID();

  @Column(name = "work_order_id", nullable = false)
  private UUID workOrderId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private WorkOrderHistoryAction action;

  @Enumerated(EnumType.STRING)
  @Column(name = "previous_status")
  private WorkOrderStatus previousStatus;

  @Enumerated(EnumType.STRING)
  @Column(name = "new_status")
  private WorkOrderStatus newStatus;

  @Column(name = "change_note", length = 1000)
  private String changeNote;

  @Column(name = "performed_by", nullable = false, length = 180)
  private String performedBy;

  @Column(name = "occurred_at", nullable = false)
  private Instant occurredAt = Instant.now();

  protected WorkOrderHistory() {}

  public WorkOrderHistory(
      UUID workOrderId,
      WorkOrderHistoryAction action,
      WorkOrderStatus previousStatus,
      WorkOrderStatus newStatus,
      String changeNote,
      String performedBy) {
    this.workOrderId = workOrderId;
    this.action = action;
    this.previousStatus = previousStatus;
    this.newStatus = newStatus;
    this.changeNote = changeNote;
    this.performedBy = performedBy;
  }

  public UUID getId() {
    return id;
  }

  public WorkOrderHistoryAction getAction() {
    return action;
  }

  public WorkOrderStatus getPreviousStatus() {
    return previousStatus;
  }

  public WorkOrderStatus getNewStatus() {
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
