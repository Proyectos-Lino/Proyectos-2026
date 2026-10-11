package com.labcore.platform.catalog;

import com.labcore.platform.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "laboratory_service")
public class LaboratoryService extends BaseEntity {

  @Id private UUID id = UUID.randomUUID();
  private String code;
  private String name;

  @Column(name = "laboratory_area_id")
  private UUID laboratoryAreaId;

  private boolean active = true;

  protected LaboratoryService() {}

  public LaboratoryService(String code, String name, UUID laboratoryAreaId) {
    this.code = code;
    this.name = name;
    this.laboratoryAreaId = laboratoryAreaId;
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

  public UUID getLaboratoryAreaId() {
    return laboratoryAreaId;
  }

  public boolean isActive() {
    return active;
  }

  public void update(String code, String name, UUID laboratoryAreaId) {
    this.code = code;
    this.name = name;
    this.laboratoryAreaId = laboratoryAreaId;
  }

  public void activate() {
    active = true;
  }

  public void deactivate() {
    active = false;
  }
}
