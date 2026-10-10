package com.labcore.platform.catalog;

import com.labcore.platform.common.BaseEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "laboratory_area")
public class LaboratoryArea extends BaseEntity {

  @Id private UUID id = UUID.randomUUID();
  private String code;
  private String name;
  private boolean active = true;

  protected LaboratoryArea() {}

  public LaboratoryArea(String code, String name) {
    this.code = code;
    this.name = name;
  }

  public UUID getId() {
    return id;
  }

  public String getCode() {
    return code;
  }

  public String getName() {
    return name;
  }

  public boolean isActive() {
    return active;
  }

  public void deactivate() {
    active = false;
  }

  public void activate() {
    active = true;
  }

  public void update(String code, String name) {
    this.code = code;
    this.name = name;
  }
}
