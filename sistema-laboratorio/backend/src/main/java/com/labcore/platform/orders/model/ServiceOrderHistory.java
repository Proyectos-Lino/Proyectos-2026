package com.labcore.platform.orders;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "service_order_history")
public class ServiceOrderHistory {

  @Id private UUID id = UUID.randomUUID();

  @Column(name = "service_order_id", nullable = false)
  private UUID serviceOrderId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private ServiceOrderHistoryAction action;

  @Enumerated(EnumType.STRING)
  @Column(name = "previous_status")
  private ServiceOrderStatus previousStatus;

  @Enumerated(EnumType.STRING)
  @Column(name = "new_status")
  private ServiceOrderStatus newStatus;

  @Column(name = "change_note", length = 1000)
  private String changeNote;

  @Column(name = "performed_by", nullable = false)
  private String performedBy;

  @Column(name = "occurred_at", nullable = false)
  private Instant occurredAt = Instant.now();

  protected ServiceOrderHistory() {}

  public ServiceOrderHistory(
      UUID serviceOrderId,
      ServiceOrderHistoryAction action,
      ServiceOrderStatus previousStatus,
      ServiceOrderStatus newStatus,
      String changeNote,
      String performedBy) {
    this.serviceOrderId = serviceOrderId;
    this.action = action;
    this.previousStatus = previousStatus;
    this.newStatus = newStatus;
    this.changeNote = changeNote;
    this.performedBy = performedBy;
  }

  public UUID getId() {
    return id;
  }

  public ServiceOrderHistoryAction getAction() {
    return action;
  }

  public ServiceOrderStatus getPreviousStatus() {
    return previousStatus;
  }

  public ServiceOrderStatus getNewStatus() {
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
