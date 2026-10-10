package com.labcore.platform.results;

import com.labcore.platform.catalog.ResultMode;
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
@Table(name = "result_entry")
public class ResultEntry extends BaseEntity {
  @Id private UUID id = UUID.randomUUID();

  @Column(name = "work_order_test_id")
  private UUID workOrderTestId;

  @Column(name = "result_template_id")
  private UUID resultTemplateId;

  @Enumerated(EnumType.STRING)
  private ResultMode resultMode = ResultMode.SCALAR;

  @Enumerated(EnumType.STRING)
  private ResultEntryStatus status = ResultEntryStatus.RECORDED;

  private String analystObservation;

  @Column(name = "recorded_by")
  private UUID recordedBy;

  private Instant recordedAt;

  @Column(name = "result_revision")
  private int resultRevision = 1;

  private String correctionRequestReason;

  @Column(name = "correction_requested_by")
  private UUID correctionRequestedBy;

  private String correctionRequestedByRole;
  private Instant correctionRequestedAt;
  private Instant invalidatedAt;

  @Column(length = 180)
  private String invalidatedBy;

  @Column(length = 1000)
  private String invalidationReason;

  protected ResultEntry() {}

  public ResultEntry(UUID workOrderTestId, String analystObservation, UUID recordedBy) {
    this(workOrderTestId, null, ResultMode.SCALAR, analystObservation, recordedBy);
  }

  public ResultEntry(
      UUID workOrderTestId,
      UUID resultTemplateId,
      ResultMode resultMode,
      String analystObservation,
      UUID recordedBy) {
    this.workOrderTestId = workOrderTestId;
    this.resultTemplateId = resultTemplateId;
    this.resultMode = resultMode;
    this.analystObservation = analystObservation;
    this.recordedBy = recordedBy;
    this.recordedAt = Instant.now();
  }

  public UUID getId() {
    return id;
  }

  public UUID getWorkOrderTestId() {
    return workOrderTestId;
  }

  public UUID getResultTemplateId() {
    return resultTemplateId;
  }

  public ResultMode getResultMode() {
    return resultMode;
  }

  public ResultEntryStatus getStatus() {
    return status;
  }

  public String getAnalystObservation() {
    return analystObservation;
  }

  public UUID getRecordedBy() {
    return recordedBy;
  }

  public Instant getRecordedAt() {
    return recordedAt;
  }

  public int getResultRevision() {
    return resultRevision;
  }

  public String getCorrectionRequestReason() {
    return correctionRequestReason;
  }

  public UUID getCorrectionRequestedBy() {
    return correctionRequestedBy;
  }

  public String getCorrectionRequestedByRole() {
    return correctionRequestedByRole;
  }

  public Instant getCorrectionRequestedAt() {
    return correctionRequestedAt;
  }

  public void revise(String observation) {
    analystObservation = observation;
  }

  public void markDraft() {
    status = ResultEntryStatus.DRAFT;
  }

  public void markRecorded(String observation) {
    analystObservation = observation;
    status = ResultEntryStatus.RECORDED;
  }

  public void markCorrected(String observation) {
    analystObservation = observation;
    resultRevision++;
    status = ResultEntryStatus.CORRECTED;
  }

  public void requestCorrection(String reason, UUID reviewerId, String reviewerRole) {
    status = ResultEntryStatus.CORRECTION_REQUESTED;
    correctionRequestReason = reason;
    correctionRequestedBy = reviewerId;
    correctionRequestedByRole = reviewerRole;
    correctionRequestedAt = Instant.now();
  }

  public void registerValidatorCorrection(String observation) {
    analystObservation = observation;
    resultRevision++;
  }

  public void validate() {
    status = ResultEntryStatus.VALIDATED;
  }

  public void invalidate(String reason, String actor) {
    status = ResultEntryStatus.INVALIDATED;
    invalidatedAt = Instant.now();
    invalidatedBy = actor;
    invalidationReason = reason;
  }
}
