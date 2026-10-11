package com.labcore.platform.catalog;

import com.labcore.platform.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "test_definition")
public class TestDefinition extends BaseEntity {

  @Id private UUID id = UUID.randomUUID();
  private String code;
  private String name;

  @Enumerated(EnumType.STRING)
  private ResultDataType resultType;

  @Column(name = "result_unit_id")
  private UUID resultUnitId;

  private boolean active = true;

  protected TestDefinition() {}

  public TestDefinition(String code, String name, ResultDataType resultType, UUID resultUnitId) {
    this.code = code;
    this.name = name;
    this.resultType = resultType;
    this.resultUnitId = resultUnitId;
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

  public ResultDataType getResultType() {
    return resultType;
  }

  public UUID getResultUnitId() {
    return resultUnitId;
  }

  public boolean isActive() {
    return active;
  }

  public void update(String code, String name, ResultDataType resultType, UUID resultUnitId) {
    this.code = code;
    this.name = name;
    this.resultType = resultType;
    this.resultUnitId = resultUnitId;
  }

  public void activate() {
    active = true;
  }

  public void deactivate() {
    active = false;
  }
}
