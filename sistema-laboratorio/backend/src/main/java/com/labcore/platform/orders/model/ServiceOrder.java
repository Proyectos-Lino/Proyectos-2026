package com.labcore.platform.orders;

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
@Table(name = "service_order")
public class ServiceOrder extends BaseEntity {

  @Id private UUID id = UUID.randomUUID();
  private String code;

  @Column(name = "client_id")
  private UUID clientId;

  @Column(name = "laboratory_area_id")
  private UUID laboratoryAreaId;

  @Enumerated(EnumType.STRING)
  private ServiceOrderStatus status;

  private String reportName;
  private String reportEmail;
  private String reportAddress;

  @Enumerated(EnumType.STRING)
  private DeliveryMethod deliveryMethod;

  private String pickupPerson;
  private Instant plannedDeliveryAt;
  private String proformaNumber;
  private String technicalProposalNumber;
  private Integer developmentDays;
  private boolean outsourced;

  @Enumerated(EnumType.STRING)
  private PostAnalysisCustody postAnalysisCustody;

  private String generalObservations;
  private Instant confirmedAt;
  private Instant finalizedAt;
  private Instant cancelledAt;

  @Enumerated(EnumType.STRING)
  private ServiceOrderStatus statusBeforeCorrection;

  @Column(length = 1000)
  private String correctionReason;

  private String correctionRequestedBy;
  private Instant correctionRequestedAt;
  private String correctionSubmittedBy;
  private Instant correctionSubmittedAt;

  @Column(length = 1000)
  private String correctionChangeNote;

  private String correctionReviewedBy;
  private Instant correctionReviewedAt;

  @Column(length = 2000)
  private String correctionImpactSummary;

  private boolean correctionRequiresTechnicalWork;

  @Column(length = 1000)
  private String cancellationReason;

  protected ServiceOrder() {}

  public ServiceOrder(
      String code,
      UUID clientId,
      UUID laboratoryAreaId,
      String reportName,
      String reportEmail,
      String reportAddress,
      DeliveryMethod deliveryMethod,
      String pickupPerson,
      Instant plannedDeliveryAt,
      String proformaNumber,
      String technicalProposalNumber,
      Integer developmentDays,
      boolean outsourced,
      PostAnalysisCustody postAnalysisCustody,
      String generalObservations) {
    this.code = code;
    this.clientId = clientId;
    this.laboratoryAreaId = laboratoryAreaId;
    this.status = ServiceOrderStatus.DRAFT;
    updateCommercialDetails(
        reportName,
        reportEmail,
        reportAddress,
        deliveryMethod,
        pickupPerson,
        plannedDeliveryAt,
        proformaNumber,
        technicalProposalNumber,
        developmentDays,
        outsourced,
        postAnalysisCustody,
        generalObservations);
  }

  public UUID getId() {
    return id;
  }

  public String getCode() {
    return code;
  }

  public UUID getClientId() {
    return clientId;
  }

  public UUID getLaboratoryAreaId() {
    return laboratoryAreaId;
  }

  public ServiceOrderStatus getStatus() {
    return status;
  }

  public String getReportName() {
    return reportName;
  }

  public String getReportEmail() {
    return reportEmail;
  }

  public String getReportAddress() {
    return reportAddress;
  }

  public DeliveryMethod getDeliveryMethod() {
    return deliveryMethod;
  }

  public String getPickupPerson() {
    return pickupPerson;
  }

  public Instant getPlannedDeliveryAt() {
    return plannedDeliveryAt;
  }

  public boolean isOutsourced() {
    return outsourced;
  }

  public String getProformaNumber() {
    return proformaNumber;
  }

  public String getTechnicalProposalNumber() {
    return technicalProposalNumber;
  }

  public Integer getDevelopmentDays() {
    return developmentDays;
  }

  public PostAnalysisCustody getPostAnalysisCustody() {
    return postAnalysisCustody;
  }

  public String getGeneralObservations() {
    return generalObservations;
  }

  public Instant getFinalizedAt() {
    return finalizedAt;
  }

  public Instant getConfirmedAt() {
    return confirmedAt;
  }

  public Instant getCancelledAt() {
    return cancelledAt;
  }

  public String getCancellationReason() {
    return cancellationReason;
  }

  public ServiceOrderStatus getStatusBeforeCorrection() {
    return statusBeforeCorrection;
  }

  public String getCorrectionReason() {
    return correctionReason;
  }

  public String getCorrectionRequestedBy() {
    return correctionRequestedBy;
  }

  public Instant getCorrectionRequestedAt() {
    return correctionRequestedAt;
  }

  public String getCorrectionSubmittedBy() {
    return correctionSubmittedBy;
  }

  public Instant getCorrectionSubmittedAt() {
    return correctionSubmittedAt;
  }

  public String getCorrectionChangeNote() {
    return correctionChangeNote;
  }

  public String getCorrectionReviewedBy() {
    return correctionReviewedBy;
  }

  public Instant getCorrectionReviewedAt() {
    return correctionReviewedAt;
  }

  public String getCorrectionImpactSummary() {
    return correctionImpactSummary;
  }

  public boolean isCorrectionRequiresTechnicalWork() {
    return correctionRequiresTechnicalWork;
  }

  public void updateClient(UUID clientId) {
    this.clientId = clientId;
  }

  public void updateCommercialDetails(
      String reportName,
      String reportEmail,
      String reportAddress,
      DeliveryMethod deliveryMethod,
      String pickupPerson,
      Instant plannedDeliveryAt,
      String proformaNumber,
      String technicalProposalNumber,
      Integer developmentDays,
      boolean outsourced,
      PostAnalysisCustody postAnalysisCustody,
      String generalObservations) {
    this.reportName = reportName;
    this.reportEmail = reportEmail;
    this.reportAddress = reportAddress;
    this.deliveryMethod = deliveryMethod;
    this.pickupPerson = pickupPerson;
    this.plannedDeliveryAt = plannedDeliveryAt;
    this.proformaNumber = proformaNumber;
    this.technicalProposalNumber = technicalProposalNumber;
    this.developmentDays = developmentDays;
    this.outsourced = outsourced;
    this.postAnalysisCustody = postAnalysisCustody;
    this.generalObservations = generalObservations;
  }

  public void confirmForLaboratory() {
    status = ServiceOrderStatus.IN_PROGRESS;
    confirmedAt = Instant.now();
  }

  public void completeTechnicalProcess() {
    status = ServiceOrderStatus.FINALIZED;
    finalizedAt = Instant.now();
  }

  public void cancel(String reason) {
    status = ServiceOrderStatus.CANCELLED;
    cancelledAt = Instant.now();
    cancellationReason = reason;
  }

  public void requestCommercialCorrection(String reason, String requestedBy) {
    statusBeforeCorrection = status;
    status = ServiceOrderStatus.COMMERCIAL_CORRECTION;
    correctionReason = reason;
    correctionRequestedBy = requestedBy;
    correctionRequestedAt = Instant.now();
    correctionSubmittedBy = null;
    correctionSubmittedAt = null;
    correctionChangeNote = null;
    correctionReviewedBy = null;
    correctionReviewedAt = null;
    correctionImpactSummary = null;
    correctionRequiresTechnicalWork = false;
  }

  public void registerCorrectionImpact(String summary, boolean requiresTechnicalWork) {
    correctionImpactSummary = summary;
    correctionRequiresTechnicalWork = correctionRequiresTechnicalWork || requiresTechnicalWork;
  }

  public void submitCommercialCorrection(String submittedBy, String changeNote) {
    status = ServiceOrderStatus.CORRECTION_REVIEW;
    correctionSubmittedBy = submittedBy;
    correctionSubmittedAt = Instant.now();
    correctionChangeNote = changeNote;
  }

  public void approveCommercialCorrection(String reviewedBy) {
    status =
        correctionRequiresTechnicalWork ? ServiceOrderStatus.IN_PROGRESS : statusBeforeCorrection;
    if (correctionRequiresTechnicalWork) {
      finalizedAt = null;
    }
    if (status == null || status == ServiceOrderStatus.COMMERCIAL_CORRECTION) {
      status = ServiceOrderStatus.IN_PROGRESS;
    }
    correctionReviewedBy = reviewedBy;
    correctionReviewedAt = Instant.now();
  }
}
