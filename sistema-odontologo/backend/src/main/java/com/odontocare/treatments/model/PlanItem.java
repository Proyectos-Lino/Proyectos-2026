package com.odontocare.treatments.model;

import com.odontocare.shared.model.VersionedEntity;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;

@Entity
@Table(name = "treatment_item")
public class PlanItem extends VersionedEntity {
  private UUID planId;
  private UUID serviceId;
  private String serviceName;
  private String description;
  private Integer tooth;
  private Integer quantity;
  private Integer sessions;
  private BigDecimal unitPrice;
  private Integer position;
  private int durationMinutes = 30;

  @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.JSON)
  private String scheduling = "{\"mode\":\"NONE\",\"dates\":[]}";

  public int getDurationMinutes() {
    return durationMinutes;
  }

  public void setDurationMinutes(int value) {
    durationMinutes = value;
  }

  public String getScheduling() {
    return scheduling;
  }

  public void setScheduling(String value) {
    scheduling = value;
  }

  public UUID getPlanId() {
    return planId;
  }

  public void setPlanId(UUID value) {
    this.planId = value;
  }

  public UUID getServiceId() {
    return serviceId;
  }

  public void setServiceId(UUID value) {
    this.serviceId = value;
  }

  public String getServiceName() {
    return serviceName;
  }

  public void setServiceName(String value) {
    this.serviceName = value;
  }

  public String getDescription() {
    return description;
  }

  public void setDescription(String value) {
    this.description = value;
  }

  public Integer getTooth() {
    return tooth;
  }

  public void setTooth(Integer value) {
    this.tooth = value;
  }

  public Integer getQuantity() {
    return quantity;
  }

  public void setQuantity(Integer value) {
    this.quantity = value;
  }

  public Integer getSessions() {
    return sessions;
  }

  public void setSessions(Integer value) {
    this.sessions = value;
  }

  public BigDecimal getUnitPrice() {
    return unitPrice;
  }

  public void setUnitPrice(BigDecimal value) {
    this.unitPrice = value;
  }

  public Integer getPosition() {
    return position;
  }

  public void setPosition(Integer value) {
    this.position = value;
  }
}
