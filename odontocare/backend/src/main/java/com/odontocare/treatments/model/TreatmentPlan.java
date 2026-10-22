package com.odontocare.treatments.model;

import com.odontocare.shared.model.VersionedEntity;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;

@Entity
@Table(name = "treatment_plan")
public class TreatmentPlan extends VersionedEntity {
  private int mutation;

  public void advanceRevision() {
    mutation = Math.addExact(mutation, 1);
  }

  private BigDecimal acceptedTotal;

  public BigDecimal getAcceptedTotal() {
    return acceptedTotal;
  }

  public void setAcceptedTotal(BigDecimal value) {
    acceptedTotal = value;
  }

  private UUID patientId;
  private UUID dentistId;
  private String dentistName;
  private String patientName;
  private String code;
  private String title;
  private String conditions;
  private String currency;
  private String status;
  private String acceptedBy;
  private Instant acceptedAt;

  public UUID getPatientId() {
    return patientId;
  }

  public void setPatientId(UUID value) {
    this.patientId = value;
  }

  public UUID getDentistId() {
    return dentistId;
  }

  public void setDentistId(UUID value) {
    this.dentistId = value;
  }

  public String getDentistName() {
    return dentistName;
  }

  public void setDentistName(String value) {
    this.dentistName = value;
  }

  public String getPatientName() {
    return patientName;
  }

  public void setPatientName(String value) {
    this.patientName = value;
  }

  public String getCode() {
    return code;
  }

  public void setCode(String value) {
    this.code = value;
  }

  public String getTitle() {
    return title;
  }

  public void setTitle(String value) {
    this.title = value;
  }

  public String getConditions() {
    return conditions;
  }

  public void setConditions(String value) {
    this.conditions = value;
  }

  public String getCurrency() {
    return currency;
  }

  public void setCurrency(String value) {
    this.currency = value;
  }

  public String getStatus() {
    return status;
  }

  public void setStatus(String value) {
    this.status = value;
  }

  public String getAcceptedBy() {
    return acceptedBy;
  }

  public void setAcceptedBy(String value) {
    this.acceptedBy = value;
  }

  public Instant getAcceptedAt() {
    return acceptedAt;
  }

  public void setAcceptedAt(Instant value) {
    this.acceptedAt = value;
  }
}
