package com.labcore.platform.results;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "result_change_log")
public class ResultChangeLog {
  @Id private UUID id = UUID.randomUUID();

  @Column(name = "result_value_id")
  private UUID resultValueId;

  private String oldValueText;
  private BigDecimal oldValueNumber;
  private String newValueText;
  private BigDecimal newValueNumber;
  private String reason;

  @Column(name = "changed_by")
  private UUID changedBy;

  private String changedByRole;

  private Instant changedAt;

  protected ResultChangeLog() {}

  public ResultChangeLog(
      ResultValue value,
      String newValueText,
      BigDecimal newValueNumber,
      String reason,
      UUID changedBy,
      String changedByRole) {
    this.resultValueId = value.getId();
    this.oldValueText = value.getValueText();
    this.oldValueNumber = value.getValueNumber();
    this.newValueText = newValueText;
    this.newValueNumber = newValueNumber;
    this.reason = reason;
    this.changedBy = changedBy;
    this.changedByRole = changedByRole;
    this.changedAt = Instant.now();
  }

  public ResultChangeLog(
      UUID resultValueId,
      String oldValueText,
      BigDecimal oldValueNumber,
      String newValueText,
      BigDecimal newValueNumber,
      String reason,
      UUID changedBy,
      String changedByRole) {
    this.resultValueId = resultValueId;
    this.oldValueText = oldValueText;
    this.oldValueNumber = oldValueNumber;
    this.newValueText = newValueText;
    this.newValueNumber = newValueNumber;
    this.reason = reason;
    this.changedBy = changedBy;
    this.changedByRole = changedByRole;
    this.changedAt = Instant.now();
  }

  public UUID getId() {
    return id;
  }

  public UUID getResultValueId() {
    return resultValueId;
  }

  public String getOldValueText() {
    return oldValueText;
  }

  public BigDecimal getOldValueNumber() {
    return oldValueNumber;
  }

  public String getNewValueText() {
    return newValueText;
  }

  public BigDecimal getNewValueNumber() {
    return newValueNumber;
  }

  public String getReason() {
    return reason;
  }

  public UUID getChangedBy() {
    return changedBy;
  }

  public Instant getChangedAt() {
    return changedAt;
  }

  public String getChangedByRole() {
    return changedByRole;
  }
}
