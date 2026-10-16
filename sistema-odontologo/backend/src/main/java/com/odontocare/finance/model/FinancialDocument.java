package com.odontocare.finance.model;

import com.odontocare.shared.model.VersionedEntity;
import jakarta.persistence.*;
import java.time.*;
import java.util.UUID;

@Entity
@Table(name = "financial_document")
public class FinancialDocument extends VersionedEntity {
  private UUID cashSessionId;

  public UUID getCashSessionId() {
    return cashSessionId;
  }

  public void setCashSessionId(UUID value) {
    cashSessionId = value;
  }

  private UUID movementId;

  public UUID getMovementId() {
    return movementId;
  }

  public void setMovementId(UUID value) {
    movementId = value;
  }

  private UUID patientId;

  public UUID getPatientId() {
    return patientId;
  }

  public void setPatientId(UUID value) {
    patientId = value;
  }

  private String fileName;

  public String getFileName() {
    return fileName;
  }

  public void setFileName(String value) {
    fileName = value;
  }

  private String mediaType;

  public String getMediaType() {
    return mediaType;
  }

  public void setMediaType(String value) {
    mediaType = value;
  }

  private Long byteSize;

  public Long getByteSize() {
    return byteSize;
  }

  public void setByteSize(Long value) {
    byteSize = value;
  }

  private String sha256;

  public String getSha256() {
    return sha256;
  }

  public void setSha256(String value) {
    sha256 = value;
  }

  private String description;

  public String getDescription() {
    return description;
  }

  public void setDescription(String value) {
    description = value;
  }

  private String actorName;

  public String getActorName() {
    return actorName;
  }

  public void setActorName(String value) {
    actorName = value;
  }

  private Boolean generated;

  public Boolean getGenerated() {
    return generated;
  }

  public void setGenerated(Boolean value) {
    generated = value;
  }
}
