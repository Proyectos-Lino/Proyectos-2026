package com.odontocare.documents.model;

import com.odontocare.shared.model.VersionedEntity;
import jakarta.persistence.*;
import java.time.*;
import java.util.*;

@Entity
@Table(name = "document_consent")
public class DocumentConsent extends VersionedEntity {
  private UUID patientId;
  private UUID documentId;
  private String name;
  private String responsible;
  private String relationship;
  private LocalDate signedOn;
  private String actorName;

  public UUID getPatientId() {
    return patientId;
  }

  public void setPatientId(UUID value) {
    this.patientId = value;
  }

  public UUID getDocumentId() {
    return documentId;
  }

  public void setDocumentId(UUID value) {
    this.documentId = value;
  }

  public String getName() {
    return name;
  }

  public void setName(String value) {
    this.name = value;
  }

  public String getResponsible() {
    return responsible;
  }

  public void setResponsible(String value) {
    this.responsible = value;
  }

  public String getRelationship() {
    return relationship;
  }

  public void setRelationship(String value) {
    this.relationship = value;
  }

  public LocalDate getSignedOn() {
    return signedOn;
  }

  public void setSignedOn(LocalDate value) {
    this.signedOn = value;
  }

  public String getActorName() {
    return actorName;
  }

  public void setActorName(String value) {
    this.actorName = value;
  }
}
