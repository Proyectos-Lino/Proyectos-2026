package com.labcore.platform.catalog;

import com.labcore.platform.common.BaseEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "sample_type")
public class SampleType extends BaseEntity {

  @Id private UUID id = UUID.randomUUID();
  private String code;
  private String name;
  private String laboratoryCodePrefix;

  @Enumerated(EnumType.STRING)
  private ResultMode resultMode;

  private boolean active = true;

  protected SampleType() {}

  public SampleType(String code, String name, String laboratoryCodePrefix, ResultMode resultMode) {
    this.code = code;
    this.name = name;
    this.laboratoryCodePrefix = laboratoryCodePrefix;
    this.resultMode = resultMode;
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

  public String getLaboratoryCodePrefix() {
    return laboratoryCodePrefix;
  }

  public ResultMode getResultMode() {
    return resultMode;
  }

  public boolean isActive() {
    return active;
  }

  public void update(String code, String name, String laboratoryCodePrefix, ResultMode resultMode) {
    this.code = code;
    this.name = name;
    this.laboratoryCodePrefix = laboratoryCodePrefix;
    this.resultMode = resultMode;
  }

  public void activate() {
    active = true;
  }

  public void deactivate() {
    active = false;
  }
}
