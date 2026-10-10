package com.labcore.platform.orders;

import com.labcore.platform.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "service_order_sample")
public class ServiceOrderSample extends BaseEntity {

  @Id private UUID id = UUID.randomUUID();

  @Column(name = "service_order_id")
  private UUID serviceOrderId;

  private int commercialSequence;
  private String clientIdentification;
  private UUID producerId;
  private String productName;
  private String varietyName;
  private UUID sampleTypeId;
  private String physicalDescription;
  private String samplingLocation;
  private String origin;
  private String samplerName;
  private Instant sampledAt;
  private BigDecimal quantity;
  private UUID measurementUnitId;
  private String containerDescription;
  private String batchNumber;
  private Integer productionYear;
  private String brand;
  private String originCertificate;
  private String sanitaryRegistration;
  private String cropAge;
  private Integer carrierCount;
  private Integer budCount;
  private boolean counterSampleInCustody;
  private String observations;
  private boolean active = true;
  private Instant archivedAt;
  private String archiveReason;

  protected ServiceOrderSample() {}

  public ServiceOrderSample(
      UUID serviceOrderId,
      int commercialSequence,
      String clientIdentification,
      UUID producerId,
      String productName,
      String varietyName,
      UUID sampleTypeId,
      String physicalDescription,
      String samplingLocation,
      String origin,
      String samplerName,
      Instant sampledAt,
      BigDecimal quantity,
      UUID measurementUnitId,
      String containerDescription,
      String batchNumber,
      Integer productionYear,
      String brand,
      String originCertificate,
      String sanitaryRegistration,
      String cropAge,
      Integer carrierCount,
      Integer budCount,
      boolean counterSampleInCustody,
      String observations) {
    this.serviceOrderId = serviceOrderId;
    this.commercialSequence = commercialSequence;
    this.clientIdentification = clientIdentification;
    this.producerId = producerId;
    this.productName = productName;
    this.varietyName = varietyName;
    this.sampleTypeId = sampleTypeId;
    this.physicalDescription = physicalDescription;
    this.samplingLocation = samplingLocation;
    this.origin = origin;
    this.samplerName = samplerName;
    this.sampledAt = sampledAt;
    this.quantity = quantity;
    this.measurementUnitId = measurementUnitId;
    this.containerDescription = containerDescription;
    this.batchNumber = batchNumber;
    this.productionYear = productionYear;
    this.brand = brand;
    this.originCertificate = originCertificate;
    this.sanitaryRegistration = sanitaryRegistration;
    this.cropAge = cropAge;
    this.carrierCount = carrierCount;
    this.budCount = budCount;
    this.counterSampleInCustody = counterSampleInCustody;
    this.observations = observations;
  }

  public UUID getId() {
    return id;
  }

  public UUID getServiceOrderId() {
    return serviceOrderId;
  }

  public int getCommercialSequence() {
    return commercialSequence;
  }

  public String getClientIdentification() {
    return clientIdentification;
  }

  public UUID getProducerId() {
    return producerId;
  }

  public String getProductName() {
    return productName;
  }

  public String getVarietyName() {
    return varietyName;
  }

  public UUID getSampleTypeId() {
    return sampleTypeId;
  }

  public String getPhysicalDescription() {
    return physicalDescription;
  }

  public String getSamplingLocation() {
    return samplingLocation;
  }

  public String getOrigin() {
    return origin;
  }

  public String getSamplerName() {
    return samplerName;
  }

  public Instant getSampledAt() {
    return sampledAt;
  }

  public BigDecimal getQuantity() {
    return quantity;
  }

  public UUID getMeasurementUnitId() {
    return measurementUnitId;
  }

  public String getContainerDescription() {
    return containerDescription;
  }

  public String getBatchNumber() {
    return batchNumber;
  }

  public Integer getProductionYear() {
    return productionYear;
  }

  public String getBrand() {
    return brand;
  }

  public String getOriginCertificate() {
    return originCertificate;
  }

  public String getSanitaryRegistration() {
    return sanitaryRegistration;
  }

  public String getCropAge() {
    return cropAge;
  }

  public Integer getCarrierCount() {
    return carrierCount;
  }

  public Integer getBudCount() {
    return budCount;
  }

  public boolean isCounterSampleInCustody() {
    return counterSampleInCustody;
  }

  public String getObservations() {
    return observations;
  }

  public boolean isActive() {
    return active;
  }

  public void changeCommercialSequence(int sequence) {
    commercialSequence = sequence;
  }

  public void update(
      int commercialSequence,
      String clientIdentification,
      UUID producerId,
      String productName,
      String varietyName,
      UUID sampleTypeId,
      String physicalDescription,
      String samplingLocation,
      String origin,
      String samplerName,
      Instant sampledAt,
      BigDecimal quantity,
      UUID measurementUnitId,
      String containerDescription,
      String batchNumber,
      Integer productionYear,
      String brand,
      String originCertificate,
      String sanitaryRegistration,
      String cropAge,
      Integer carrierCount,
      Integer budCount,
      boolean counterSampleInCustody,
      String observations) {
    this.commercialSequence = commercialSequence;
    this.clientIdentification = clientIdentification;
    this.producerId = producerId;
    this.productName = productName;
    this.varietyName = varietyName;
    this.sampleTypeId = sampleTypeId;
    this.physicalDescription = physicalDescription;
    this.samplingLocation = samplingLocation;
    this.origin = origin;
    this.samplerName = samplerName;
    this.sampledAt = sampledAt;
    this.quantity = quantity;
    this.measurementUnitId = measurementUnitId;
    this.containerDescription = containerDescription;
    this.batchNumber = batchNumber;
    this.productionYear = productionYear;
    this.brand = brand;
    this.originCertificate = originCertificate;
    this.sanitaryRegistration = sanitaryRegistration;
    this.cropAge = cropAge;
    this.carrierCount = carrierCount;
    this.budCount = budCount;
    this.counterSampleInCustody = counterSampleInCustody;
    this.observations = observations;
  }

  public void archive(String reason) {
    active = false;
    archivedAt = Instant.now();
    archiveReason = reason;
  }
}
