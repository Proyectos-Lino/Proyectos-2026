package com.odontocare.documents.model;

import com.odontocare.shared.model.VersionedEntity;
import jakarta.persistence.*;
import java.time.*;
import java.util.*;

@Entity
@Table(name = "patient_document")
public class PatientDocument extends VersionedEntity {
  private UUID planId;

  public UUID getPlanId() {
    return planId;
  }

  public void setPlanId(UUID value) {
    planId = value;
  }

  private UUID patientId;
  private UUID encounterId;
  private Integer tooth;
  private UUID categoryId;
  private String categoryName;
  private LocalDate recordedOn;
  private String description;
  private String fileName;
  private String mediaType;
  private Long byteSize;
  private String sha256;
  private String actorName;

  public UUID getPatientId() {
    return patientId;
  }

  public void setPatientId(UUID value) {
    this.patientId = value;
  }

  public UUID getEncounterId() {
    return encounterId;
  }

  public void setEncounterId(UUID value) {
    this.encounterId = value;
  }

  public Integer getTooth() {
    return tooth;
  }

  public void setTooth(Integer value) {
    this.tooth = value;
  }

  public UUID getCategoryId() {
    return categoryId;
  }

  public void setCategoryId(UUID value) {
    this.categoryId = value;
  }

  public String getCategoryName() {
    return categoryName;
  }

  public void setCategoryName(String value) {
    this.categoryName = value;
  }

  public LocalDate getRecordedOn() {
    return recordedOn;
  }

  public void setRecordedOn(LocalDate value) {
    this.recordedOn = value;
  }

  public String getDescription() {
    return description;
  }

  public void setDescription(String value) {
    this.description = value;
  }

  public String getFileName() {
    return fileName;
  }

  public void setFileName(String value) {
    this.fileName = value;
  }

  public String getMediaType() {
    return mediaType;
  }

  public void setMediaType(String value) {
    this.mediaType = value;
  }

  public Long getByteSize() {
    return byteSize;
  }

  public void setByteSize(Long value) {
    this.byteSize = value;
  }

  public String getSha256() {
    return sha256;
  }

  public void setSha256(String value) {
    this.sha256 = value;
  }

  public String getActorName() {
    return actorName;
  }

  public void setActorName(String value) {
    this.actorName = value;
  }
}
