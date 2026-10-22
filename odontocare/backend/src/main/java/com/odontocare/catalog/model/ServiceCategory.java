package com.odontocare.catalog.model;

import com.odontocare.shared.model.VersionedEntity;
import jakarta.persistence.*;
import java.util.*;

@Entity
@Table(name = "service_category")
public class ServiceCategory extends VersionedEntity {
  @Column(nullable = false, length = 100)
  private String name;

  @Column(nullable = false)
  private boolean active = true;

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public boolean getActive() {
    return active;
  }

  public void setActive(boolean active) {
    this.active = active;
  }
}
