package com.labcore.platform.catalog;

import com.labcore.platform.common.BaseEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "measurement_unit")
public class MeasurementUnit extends BaseEntity {

  @Id private UUID id = UUID.randomUUID();
  private String code;
  private String name;
  private String symbol;
  private boolean active = true;

  protected MeasurementUnit() {}

  public MeasurementUnit(String code, String name, String symbol) {
    this.code = code;
    this.name = name;
    this.symbol = symbol;
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

  public String getSymbol() {
    return symbol;
  }

  public boolean isActive() {
    return active;
  }

  public void update(String code, String name, String symbol) {
    this.code = code;
    this.name = name;
    this.symbol = symbol;
  }

  public void activate() {
    active = true;
  }

  public void deactivate() {
    active = false;
  }
}
