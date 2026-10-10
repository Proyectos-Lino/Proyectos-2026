package com.labcore.platform.workorders;

import com.labcore.platform.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "work_order_sample")
public class WorkOrderSample extends BaseEntity {

  @Id private UUID id = UUID.randomUUID();

  @Column(name = "work_order_id")
  private UUID workOrderId;

  @Column(name = "service_order_sample_id")
  private UUID serviceOrderSampleId;

  @Column(name = "technical_sample_id")
  private UUID technicalSampleId;

  @Column(name = "declared_sample_type_id")
  private UUID declaredSampleTypeId;

  @Column(name = "sample_type_id")
  private UUID sampleTypeId;

  private String laboratoryCode;
  private int laboratorySequence;

  protected WorkOrderSample() {}

  public WorkOrderSample(
      UUID workOrderId,
      UUID serviceOrderSampleId,
      UUID sampleTypeId,
      String laboratoryCode,
      int laboratorySequence) {
    this.workOrderId = workOrderId;
    this.serviceOrderSampleId = serviceOrderSampleId;
    this.sampleTypeId = sampleTypeId;
    this.laboratoryCode = laboratoryCode;
    this.laboratorySequence = laboratorySequence;
  }

  public WorkOrderSample(UUID workOrderId, TechnicalSample technicalSample) {
    this.workOrderId = workOrderId;
    this.serviceOrderSampleId = technicalSample.getServiceOrderSampleId();
    this.technicalSampleId = technicalSample.getId();
    this.declaredSampleTypeId = technicalSample.getDeclaredSampleTypeId();
    this.sampleTypeId = technicalSample.getTechnicalSampleTypeId();
    this.laboratoryCode = technicalSample.getLaboratoryCode();
    this.laboratorySequence = technicalSample.getLaboratorySequence();
  }

  public UUID getId() {
    return id;
  }

  public UUID getWorkOrderId() {
    return workOrderId;
  }

  public UUID getServiceOrderSampleId() {
    return serviceOrderSampleId;
  }

  public UUID getTechnicalSampleId() {
    return technicalSampleId;
  }

  public UUID getDeclaredSampleTypeId() {
    return declaredSampleTypeId;
  }

  public UUID getSampleTypeId() {
    return sampleTypeId;
  }

  public String getLaboratoryCode() {
    return laboratoryCode;
  }
}
