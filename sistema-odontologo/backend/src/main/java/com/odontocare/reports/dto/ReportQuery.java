package com.odontocare.reports.dto;

import com.odontocare.shared.pagination.PageQuery;
import jakarta.validation.constraints.*;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;

public class ReportQuery extends PageQuery {
  @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
  private LocalDate from;

  @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
  private LocalDate to;

  private UUID dentistId;
  private UUID patientId;
  private boolean includeEarlier;

  @Pattern(regexp = "[A-Z]{3}")
  private String currency;

  @Pattern(regexp = "CASH|TRANSFER|CARD|OTHER")
  private String method;

  @Size(max = 32)
  private String status;

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

  public UUID getPatientId() {
    return patientId;
  }

  public boolean isIncludeEarlier() {
    return includeEarlier;
  }

  public void setIncludeEarlier(boolean value) {
    includeEarlier = value;
  }

  public void setPatientId(UUID value) {
    patientId = value;
  }

  public String getCurrency() {
    return currency;
  }

  public void setCurrency(String value) {
    currency = blank(value);
  }

  public String getMethod() {
    return method;
  }

  public void setMethod(String value) {
    method = blank(value);
  }

  public String getStatus() {
    return status;
  }

  public void setStatus(String value) {
    status = blank(value);
  }

  private String blank(String value) {
    return value == null || value.isBlank() ? null : value.strip();
  }
}
