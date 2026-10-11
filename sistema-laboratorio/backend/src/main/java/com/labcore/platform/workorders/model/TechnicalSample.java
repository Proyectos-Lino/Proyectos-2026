package com.labcore.platform.workorders;

import com.labcore.platform.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "technical_sample")
public class TechnicalSample extends BaseEntity {

  @Id private UUID id = UUID.randomUUID();

  @Column(name = "service_order_sample_id", nullable = false, unique = true)
  private UUID serviceOrderSampleId;

  @Column(name = "declared_sample_type_id", nullable = false)
  private UUID declaredSampleTypeId;

  @Column(name = "technical_sample_type_id", nullable = false)
  private UUID technicalSampleTypeId;

  @Column(name = "laboratory_code", nullable = false, unique = true)
  private String laboratoryCode;

  @Column(name = "laboratory_sequence", nullable = false)
  private int laboratorySequence;

  @Column(name = "classified_by", nullable = false)
  private String classifiedBy;

  @Column(name = "classified_at", nullable = false)
  private Instant classifiedAt = Instant.now();

  protected TechnicalSample() {}

  public TechnicalSample(
      UUID serviceOrderSampleId,
      UUID declaredSampleTypeId,
      UUID technicalSampleTypeId,
      String laboratoryCode,
      int laboratorySequence,
      String classifiedBy) {
    this.serviceOrderSampleId = serviceOrderSampleId;
    this.declaredSampleTypeId = declaredSampleTypeId;
    this.technicalSampleTypeId = technicalSampleTypeId;
    this.laboratoryCode = laboratoryCode;
    this.laboratorySequence = laboratorySequence;
    this.classifiedBy = classifiedBy;
  }

  public UUID getId() {
    return id;
  }

  public UUID getServiceOrderSampleId() {
    return serviceOrderSampleId;
  }

  public UUID getDeclaredSampleTypeId() {
    return declaredSampleTypeId;
  }

  public UUID getTechnicalSampleTypeId() {
    return technicalSampleTypeId;
  }

  public String getLaboratoryCode() {
    return laboratoryCode;
  }

  public int getLaboratorySequence() {
    return laboratorySequence;
  }

  public String getClassifiedBy() {
    return classifiedBy;
  }

  public Instant getClassifiedAt() {
    return classifiedAt;
  }
}
