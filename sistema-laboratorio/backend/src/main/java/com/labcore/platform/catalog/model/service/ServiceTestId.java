package com.labcore.platform.catalog;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

@Embeddable
public class ServiceTestId implements Serializable {

  @Column(name = "service_id")
  private UUID serviceId;

  @Column(name = "test_definition_id")
  private UUID testDefinitionId;

  protected ServiceTestId() {}

  public ServiceTestId(UUID serviceId, UUID testDefinitionId) {
    this.serviceId = serviceId;
    this.testDefinitionId = testDefinitionId;
  }

  public UUID getServiceId() {
    return serviceId;
  }

  public UUID getTestDefinitionId() {
    return testDefinitionId;
  }

  @Override
  public boolean equals(Object other) {
    if (this == other) return true;
    if (!(other instanceof ServiceTestId that)) return false;
    return Objects.equals(serviceId, that.serviceId)
        && Objects.equals(testDefinitionId, that.testDefinitionId);
  }

  @Override
  public int hashCode() {
    return Objects.hash(serviceId, testDefinitionId);
  }
}
