package com.labcore.platform.workorders;

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
@Table(name = "work_order")
public class WorkOrder extends BaseEntity {

  @Id private UUID id = UUID.randomUUID();
  private String code;

  @Column(name = "service_order_id")
  private UUID serviceOrderId;

  @Column(name = "laboratory_area_id")
  private UUID laboratoryAreaId;

  @Column(name = "responsible_id")
  private UUID responsibleId;

  private Instant laboratoryDueAt;

  @Enumerated(EnumType.STRING)
  private WorkOrderStatus status = WorkOrderStatus.PENDING;

  private Instant cancelledAt;

  @Column(length = 1000)
  private String cancellationReason;

  private Instant closedAt;

  @Enumerated(EnumType.STRING)
  private WorkOrderStatus statusBeforeCorrection;

  protected WorkOrder() {}

  public WorkOrder(
      String code,
      UUID serviceOrderId,
      UUID laboratoryAreaId,
      UUID responsibleId,
      Instant laboratoryDueAt) {
    this.code = code;
    this.serviceOrderId = serviceOrderId;
    this.laboratoryAreaId = laboratoryAreaId;
    this.responsibleId = responsibleId;
    this.laboratoryDueAt = laboratoryDueAt;
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

  public UUID getLaboratoryAreaId() {
    return laboratoryAreaId;
  }

  public UUID getResponsibleId() {
    return responsibleId;
  }

  public Instant getLaboratoryDueAt() {
    return laboratoryDueAt;
  }

  public WorkOrderStatus getStatus() {
    return status;
  }

  public Instant getCancelledAt() {
    return cancelledAt;
  }

  public String getCancellationReason() {
    return cancellationReason;
  }

  public Instant getClosedAt() {
    return closedAt;
  }

  public WorkOrderStatus getStatusBeforeCorrection() {
    return statusBeforeCorrection;
  }

  public void markInProgress() {
    status = WorkOrderStatus.IN_PROGRESS;
  }

  public void markResultsRecorded() {
    status = WorkOrderStatus.RESULTS_RECORDED;
  }

  public void markValidated() {
    status = WorkOrderStatus.VALIDATED;
  }

  public void close() {
    status = WorkOrderStatus.CLOSED;
    closedAt = Instant.now();
  }

  public void cancel(String reason) {
    status = WorkOrderStatus.CANCELLED;
    cancellationReason = reason;
    cancelledAt = Instant.now();
  }

  public void pauseForCommercialCorrection() {
    if (status != WorkOrderStatus.CANCELLED && status != WorkOrderStatus.COMMERCIAL_CORRECTION) {
      statusBeforeCorrection = status;
      status = WorkOrderStatus.COMMERCIAL_CORRECTION;
    }
  }

  public void resumeAfterCommercialCorrection() {
    if (status == WorkOrderStatus.COMMERCIAL_CORRECTION) {
      status =
          statusBeforeCorrection == null ? WorkOrderStatus.IN_PROGRESS : statusBeforeCorrection;
    }
  }
}
