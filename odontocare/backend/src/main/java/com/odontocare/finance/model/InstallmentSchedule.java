package com.odontocare.finance.model;

import com.odontocare.shared.model.VersionedEntity;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.UUID;

@Entity
@Table(name = "installment_schedule")
public class InstallmentSchedule extends VersionedEntity {
  private UUID patientId;

  public UUID getPatientId() {
    return patientId;
  }

  public void setPatientId(UUID value) {
    patientId = value;
  }

  private UUID chargeId;

  public UUID getChargeId() {
    return chargeId;
  }

  public void setChargeId(UUID value) {
    chargeId = value;
  }

  private BigDecimal total;

  public BigDecimal getTotal() {
    return total;
  }

  public void setTotal(BigDecimal value) {
    total = value;
  }

  private Boolean active;

  public Boolean getActive() {
    return active;
  }

  public void setActive(Boolean value) {
    active = value;
  }

  private String reason;

  public String getReason() {
    return reason;
  }

  public void setReason(String value) {
    reason = value;
  }

  private String actorName;

  public String getActorName() {
    return actorName;
  }

  public void setActorName(String value) {
    actorName = value;
  }
}
