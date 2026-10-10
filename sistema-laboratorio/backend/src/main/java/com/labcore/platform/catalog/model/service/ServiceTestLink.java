package com.labcore.platform.catalog;

import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "service_test")
public class ServiceTestLink {

  @EmbeddedId private ServiceTestId id;

  private int displayOrder;

  private boolean required = true;

  protected ServiceTestLink() {}

  public ServiceTestLink(ServiceTestId id, int displayOrder) {
    this.id = id;
    this.displayOrder = displayOrder;
  }

  public ServiceTestId getId() {
    return id;
  }

  public int getDisplayOrder() {
    return displayOrder;
  }

  public boolean isRequired() {
    return required;
  }

  public void markOptional() {
    required = false;
  }
}
