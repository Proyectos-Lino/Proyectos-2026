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
@Table(name = "work_order_test")
public class WorkOrderTest extends BaseEntity {

  @Id private UUID id = UUID.randomUUID();

  @Column(name = "work_order_sample_id")
  private UUID workOrderSampleId;

  @Column(name = "source_sample_test_id")
  private UUID sourceSampleTestId;

  @Column(name = "assigned_analyst_id")
  private UUID assignedAnalystId;

  @Column(name = "test_method_id")
  private UUID testMethodId;

  @Enumerated(EnumType.STRING)
  private WorkOrderTestStatus status = WorkOrderTestStatus.PENDING;

  private Instant invalidatedAt;

  @Column(length = 180)
  private String invalidatedBy;

  @Column(length = 1000)
  private String invalidationReason;

  protected WorkOrderTest() {}

  public WorkOrderTest(
      UUID workOrderSampleId, UUID sourceSampleTestId, UUID assignedAnalystId, UUID testMethodId) {
    this.workOrderSampleId = workOrderSampleId;
    this.sourceSampleTestId = sourceSampleTestId;
    this.assignedAnalystId = assignedAnalystId;
    this.testMethodId = testMethodId;
  }

  public UUID getId() {
    return id;
  }

  public UUID getWorkOrderSampleId() {
    return workOrderSampleId;
  }

  public UUID getSourceSampleTestId() {
    return sourceSampleTestId;
  }

  public UUID getAssignedAnalystId() {
    return assignedAnalystId;
  }

  public UUID getTestMethodId() {
    return testMethodId;
  }

  public WorkOrderTestStatus getStatus() {
    return status;
  }

  public void markRecorded() {
    status = WorkOrderTestStatus.RECORDED;
  }

  public void requestCorrection() {
    status = WorkOrderTestStatus.CORRECTION_REQUESTED;
  }

  public void markCorrected() {
    status = WorkOrderTestStatus.CORRECTED;
  }

  public void markValidated() {
    status = WorkOrderTestStatus.VALIDATED;
  }

  public void invalidate(String reason, String actor) {
    status = WorkOrderTestStatus.INVALIDATED;
    invalidatedAt = Instant.now();
    invalidatedBy = actor;
    invalidationReason = reason;
  }
}
