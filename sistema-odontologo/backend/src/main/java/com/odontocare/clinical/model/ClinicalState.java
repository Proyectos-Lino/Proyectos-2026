package com.odontocare.clinical.model;

import com.odontocare.shared.model.VersionedEntity;
import jakarta.persistence.*;
import java.time.*;
import java.util.*;

@Entity
@Table(name = "clinical_state")
public class ClinicalState extends VersionedEntity {
  private Integer sequence;
  private UUID dentistId;
  private String dentistName;

  public Integer getSequence() {
    return sequence;
  }

  public void setSequence(Integer value) {
    sequence = value;
  }

  public UUID getDentistId() {
    return dentistId;
  }

  public void setDentistId(UUID value) {
    dentistId = value;
  }

  public String getDentistName() {
    return dentistName;
  }

  public void setDentistName(String value) {
    dentistName = value;
  }

  private UUID patientId;
  private String kind;
  private LocalDate recordedOn;
  private UUID previousId;

  @Column(columnDefinition = "text")
  private String payload;

  private String actorName;
  private String reason;

  public UUID getPatientId() {
    return patientId;
  }

  public void setPatientId(UUID value) {
    this.patientId = value;
  }

  public String getKind() {
    return kind;
  }

  public void setKind(String value) {
    this.kind = value;
  }

  public LocalDate getRecordedOn() {
    return recordedOn;
  }

  public void setRecordedOn(LocalDate value) {
    this.recordedOn = value;
  }

  public UUID getPreviousId() {
    return previousId;
  }

  public void setPreviousId(UUID value) {
    this.previousId = value;
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

  public String getReason() {
    return reason;
  }

  public void setReason(String value) {
    this.reason = value;
  }
}
