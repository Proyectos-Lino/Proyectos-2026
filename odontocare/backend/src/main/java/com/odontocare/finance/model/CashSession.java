package com.odontocare.finance.model;

import com.odontocare.shared.model.VersionedEntity;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.*;

@Entity
@Table(name = "cash_session")
public class CashSession extends VersionedEntity {
  private String currency;

  public String getCurrency() {
    return currency;
  }

  public void setCurrency(String value) {
    currency = value;
  }

  private BigDecimal opening;

  public BigDecimal getOpening() {
    return opening;
  }

  public void setOpening(BigDecimal value) {
    opening = value;
  }

  private String openedBy;

  public String getOpenedBy() {
    return openedBy;
  }

  public void setOpenedBy(String value) {
    openedBy = value;
  }

  private String closedBy;

  public String getClosedBy() {
    return closedBy;
  }

  public void setClosedBy(String value) {
    closedBy = value;
  }

  private Instant closedAt;

  public Instant getClosedAt() {
    return closedAt;
  }

  public void setClosedAt(Instant value) {
    closedAt = value;
  }

  private BigDecimal expected;

  public BigDecimal getExpected() {
    return expected;
  }

  public void setExpected(BigDecimal value) {
    expected = value;
  }

  private BigDecimal counted;

  public BigDecimal getCounted() {
    return counted;
  }

  public void setCounted(BigDecimal value) {
    counted = value;
  }

  private BigDecimal difference;

  public BigDecimal getDifference() {
    return difference;
  }

  public void setDifference(BigDecimal value) {
    difference = value;
  }

  private String reason;

  public String getReason() {
    return reason;
  }

  public void setReason(String value) {
    reason = value;
  }
}
