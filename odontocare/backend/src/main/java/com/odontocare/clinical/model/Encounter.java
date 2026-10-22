package com.odontocare.clinical.model;

import com.odontocare.shared.model.VersionedEntity;
import jakarta.persistence.*;
import java.time.*;
import java.util.*;

@Entity
@Table(name = "clinical_encounter")
public class Encounter extends VersionedEntity {
  private UUID patientId;
  private UUID dentistId;
  private UUID appointmentId;
  private LocalDate attendedOn;
  private String reason;
  private String status = "DRAFT";

  @Column(columnDefinition = "text")
  private String draft;

  private Integer revision = 0;

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

  public UUID getAppointmentId() {
    return appointmentId;
  }

  public void setAppointmentId(UUID value) {
    this.appointmentId = value;
  }

  public LocalDate getAttendedOn() {
    return attendedOn;
  }

  public void setAttendedOn(LocalDate value) {
    this.attendedOn = value;
  }

  public String getReason() {
    return reason;
  }

  public void setReason(String value) {
    this.reason = value;
  }

  public String getStatus() {
    return status;
  }

  public void setStatus(String value) {
    this.status = value;
  }

  public String getDraft() {
    return draft;
  }

  public void setDraft(String value) {
    this.draft = value;
  }

  public Integer getRevision() {
    return revision;
  }

  public void setRevision(Integer value) {
    this.revision = value;
  }
}
