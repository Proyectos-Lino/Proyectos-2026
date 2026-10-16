package com.odontocare.clinical.model;

import com.odontocare.shared.model.VersionedEntity;
import jakarta.persistence.*;
import java.time.*;
import java.util.*;

@Entity
@Table(name = "encounter_revision")
public class EncounterRevision extends VersionedEntity {
  private UUID encounterId;
  private Integer number;
  private String patientName;
  private String patientCode;
  private LocalDate birthDate;
  private String patientDocument;
  private String dentistName;
  private LocalDate attendedOn;
  private String reason;

  @Column(columnDefinition = "text")
  private String payload;

  private String actorName;
  private String correctionReason;

  public UUID getEncounterId() {
    return encounterId;
  }

  public void setEncounterId(UUID value) {
    this.encounterId = value;
  }

  public Integer getNumber() {
    return number;
  }

  public void setNumber(Integer value) {
    this.number = value;
  }

  public String getPatientName() {
    return patientName;
  }

  public void setPatientName(String value) {
    this.patientName = value;
  }

  public String getPatientCode() {
    return patientCode;
  }

  public void setPatientCode(String value) {
    this.patientCode = value;
  }

  public LocalDate getBirthDate() {
    return birthDate;
  }

  public void setBirthDate(LocalDate value) {
    this.birthDate = value;
  }

  public String getPatientDocument() {
    return patientDocument;
  }

  public void setPatientDocument(String value) {
    this.patientDocument = value;
  }

  public String getDentistName() {
    return dentistName;
  }

  public void setDentistName(String value) {
    this.dentistName = value;
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

  public String getPayload() {
    return payload;
  }

  public void setPayload(String value) {
    this.payload = value;
  }

  public String getActorName() {
    return actorName;
  }

  public void setActorName(String value) {
    this.actorName = value;
  }

  public String getCorrectionReason() {
    return correctionReason;
  }

  public void setCorrectionReason(String value) {
    this.correctionReason = value;
  }
}
