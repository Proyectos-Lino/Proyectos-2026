package com.odontocare.finance.model;

import com.odontocare.shared.model.VersionedEntity;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;

@Entity
@Table(name = "charge_entry")
public class ChargeEntry extends VersionedEntity {
  private UUID patientId;
  private UUID planId;
  private UUID itemId;
  private UUID encounterId;
  private UUID originalId;
  private String sourceKey;
  private String fingerprint;
  private String kind;
  private String description;
  private String currency;
  private BigDecimal amount;
  private BigDecimal unitPrice;
  private Integer quantity;
  private String reason;
  private String actorName;

  public UUID getPatientId() {
    return patientId;
  }

  public void setPatientId(UUID value) {
    this.patientId = value;
  }

  public UUID getPlanId() {
    return planId;
  }

  public void setPlanId(UUID value) {
    this.planId = value;
  }

  public UUID getItemId() {
    return itemId;
  }

  public void setItemId(UUID value) {
    this.itemId = value;
  }

  public UUID getEncounterId() {
    return encounterId;
  }

  public void setEncounterId(UUID value) {
    this.encounterId = value;
  }

  public UUID getOriginalId() {
    return originalId;
  }

  public void setOriginalId(UUID value) {
    this.originalId = value;
  }

  public String getSourceKey() {
    return sourceKey;
  }

  public void setSourceKey(String value) {
    this.sourceKey = value;
  }

  public String getFingerprint() {
    return fingerprint;
  }

  public void setFingerprint(String value) {
    this.fingerprint = value;
  }

  public String getKind() {
    return kind;
  }

  public void setKind(String value) {
    this.kind = value;
  }

  public String getDescription() {
    return description;
  }

  public void setDescription(String value) {
    this.description = value;
  }

  public String getCurrency() {
    return currency;
  }

  public void setCurrency(String value) {
    this.currency = value;
  }

  public BigDecimal getAmount() {
    return amount;
  }

  public void setAmount(BigDecimal value) {
    this.amount = value;
  }

  public BigDecimal getUnitPrice() {
    return unitPrice;
  }

  public void setUnitPrice(BigDecimal value) {
    this.unitPrice = value;
  }

  public Integer getQuantity() {
    return quantity;
  }

  public void setQuantity(Integer value) {
    this.quantity = value;
  }

  public String getReason() {
    return reason;
  }

  public void setReason(String value) {
    this.reason = value;
  }

  public String getActorName() {
    return actorName;
  }

  public void setActorName(String value) {
    this.actorName = value;
  }
}
