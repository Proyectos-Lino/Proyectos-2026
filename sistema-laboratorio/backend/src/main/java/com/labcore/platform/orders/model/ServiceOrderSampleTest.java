package com.labcore.platform.orders;

import com.labcore.platform.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "service_order_sample_test")
public class ServiceOrderSampleTest extends BaseEntity {

  @Id private UUID id = UUID.randomUUID();

  @Column(name = "service_order_sample_id")
  private UUID serviceOrderSampleId;

  @Column(name = "laboratory_service_id")
  private UUID laboratoryServiceId;

  @Column(name = "test_definition_id")
  private UUID testDefinitionId;

  @Column(name = "test_method_id")
  private UUID testMethodId;

  @Enumerated(EnumType.STRING)
  private RequestedTestStatus status = RequestedTestStatus.PENDING;

  protected ServiceOrderSampleTest() {}

  public ServiceOrderSampleTest(
      UUID serviceOrderSampleId,
      UUID laboratoryServiceId,
      UUID testDefinitionId,
      UUID testMethodId) {
    this.serviceOrderSampleId = serviceOrderSampleId;
    this.laboratoryServiceId = laboratoryServiceId;
    this.testDefinitionId = testDefinitionId;
    this.testMethodId = testMethodId;
  }

  public UUID getId() {
    return id;
  }

  public UUID getServiceOrderSampleId() {
    return serviceOrderSampleId;
  }

  public UUID getLaboratoryServiceId() {
    return laboratoryServiceId;
  }

  public UUID getTestDefinitionId() {
    return testDefinitionId;
  }

  public UUID getTestMethodId() {
    return testMethodId;
  }

  public RequestedTestStatus getStatus() {
    return status;
  }

  public void markAssigned() {
    status = RequestedTestStatus.ASSIGNED;
  }

  public void markPending() {
    status = RequestedTestStatus.PENDING;
  }

  public void markCompleted() {
    status = RequestedTestStatus.COMPLETED;
  }

  public void cancel() {
    status = RequestedTestStatus.CANCELLED;
  }
}
