package com.odontocare.finance.model;

import com.odontocare.shared.model.VersionedEntity;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.UUID;

@Entity
@Table(name = "money_movement")
public class MoneyMovement extends VersionedEntity {
  private UUID patientId;

  public UUID getPatientId() {
    return patientId;
  }

  public void setPatientId(UUID value) {
    patientId = value;
  }

  private UUID originalId;

  public UUID getOriginalId() {
    return originalId;
  }

  public void setOriginalId(UUID value) {
    originalId = value;
  }

  private UUID cashSessionId;

  public UUID getCashSessionId() {
    return cashSessionId;
  }

  public void setCashSessionId(UUID value) {
    cashSessionId = value;
  }

  private UUID categoryId;

  public UUID getCategoryId() {
    return categoryId;
  }

  public void setCategoryId(UUID value) {
    categoryId = value;
  }

  private String kind;

  public String getKind() {
    return kind;
  }

  public void setKind(String value) {
    kind = value;
  }

  private String currency;

  public String getCurrency() {
    return currency;
  }

  public void setCurrency(String value) {
    currency = value;
  }

  private BigDecimal amount;

  public BigDecimal getAmount() {
    return amount;
  }

  public void setAmount(BigDecimal value) {
    amount = value;
  }

  private LocalDate occurredOn;

  public LocalDate getOccurredOn() {
    return occurredOn;
  }

  public void setOccurredOn(LocalDate value) {
    occurredOn = value;
  }

  private String method;

  public String getMethod() {
    return method;
  }

  public void setMethod(String value) {
    method = value;
  }

  private String reference;

  public String getReference() {
    return reference;
  }

  public void setReference(String value) {
    reference = value;
  }

  private String description;

  public String getDescription() {
    return description;
  }

  public void setDescription(String value) {
    description = value;
  }

  private String supplier;

  public String getSupplier() {
    return supplier;
  }

  public void setSupplier(String value) {
    supplier = value;
  }

  private String actorName;

  public String getActorName() {
    return actorName;
  }

  public void setActorName(String value) {
    actorName = value;
  }

  private String categoryName;

  public String getCategoryName() {
    return categoryName;
  }

  public void setCategoryName(String value) {
    categoryName = value;
  }

  private String receiptCode;

  public String getReceiptCode() {
    return receiptCode;
  }

  public void setReceiptCode(String value) {
    receiptCode = value;
  }
}
