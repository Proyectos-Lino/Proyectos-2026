package com.odontocare.finance.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.util.UUID;

@Entity
@org.hibernate.annotations.Immutable
@Table(name = "financial_charge_balance")
public class ChargeBalance {
  @Id private UUID id;
  private UUID patientId;
  private String description;
  private String currency;
  private BigDecimal debt;
  private BigDecimal applied;
  private BigDecimal pending;
  private boolean closed;
  private UUID encounterId;

  public boolean isClosed() {
    return closed;
  }

  public UUID getEncounterId() {
    return encounterId;
  }

  public UUID getId() {
    return id;
  }

  public UUID getPatientId() {
    return patientId;
  }

  public String getDescription() {
    return description;
  }

  public String getCurrency() {
    return currency;
  }

  public BigDecimal getDebt() {
    return debt;
  }

  public BigDecimal getApplied() {
    return applied;
  }

  public BigDecimal getPending() {
    return pending;
  }
}
