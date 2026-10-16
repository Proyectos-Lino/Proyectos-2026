package com.odontocare.finance.model;

import com.odontocare.shared.model.VersionedEntity;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.UUID;

@Entity
@Table(name = "installment")
public class Installment extends VersionedEntity {
  private UUID scheduleId;

  public UUID getScheduleId() {
    return scheduleId;
  }

  public void setScheduleId(UUID value) {
    scheduleId = value;
  }

  private Integer position;

  public Integer getPosition() {
    return position;
  }

  public void setPosition(Integer value) {
    position = value;
  }

  private LocalDate dueOn;

  public LocalDate getDueOn() {
    return dueOn;
  }

  public void setDueOn(LocalDate value) {
    dueOn = value;
  }

  private BigDecimal amount;

  public BigDecimal getAmount() {
    return amount;
  }

  public void setAmount(BigDecimal value) {
    amount = value;
  }
}
