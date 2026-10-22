package com.odontocare.dashboard.dto;

import java.time.LocalDate;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;

public class DashboardQuery {
  @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
  private LocalDate day;

  @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
  private LocalDate from;

  @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
  private LocalDate to;

  private UUID dentistId;

  public LocalDate getDay() {
    return day;
  }

  public void setDay(LocalDate value) {
    day = value;
  }

  public LocalDate getFrom() {
    return from;
  }

  public void setFrom(LocalDate value) {
    from = value;
  }

  public LocalDate getTo() {
    return to;
  }

  public void setTo(LocalDate value) {
    to = value;
  }

  public UUID getDentistId() {
    return dentistId;
  }

  public void setDentistId(UUID value) {
    dentistId = value;
  }
}
