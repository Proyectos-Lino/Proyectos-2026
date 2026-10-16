package com.odontocare.finance.model;

import com.odontocare.shared.model.VersionedEntity;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.UUID;

@Entity
@Table(name = "money_application")
public class MoneyApplication extends VersionedEntity {
  private UUID patientId;

  public UUID getPatientId() {
    return patientId;
  }

  public void setPatientId(UUID value) {
    patientId = value;
  }

  private UUID paymentId;

  public UUID getPaymentId() {
    return paymentId;
  }

  public void setPaymentId(UUID value) {
    paymentId = value;
  }

  private UUID chargeId;

  public UUID getChargeId() {
    return chargeId;
  }

  public void setChargeId(UUID value) {
    chargeId = value;
  }

  private UUID operationId;

  public UUID getOperationId() {
    return operationId;
  }

  public void setOperationId(UUID value) {
    operationId = value;
  }

  private BigDecimal amount;

  public BigDecimal getAmount() {
    return amount;
  }

  public void setAmount(BigDecimal value) {
    amount = value;
  }
}
