package com.labcore.platform.client;

import com.labcore.platform.common.BaseEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "client")
public class Client extends BaseEntity {

  @Id private UUID id = UUID.randomUUID();

  @Enumerated(EnumType.STRING)
  private DocumentType documentType;

  @Enumerated(EnumType.STRING)
  private ParticipantType participantType;

  private String documentNumber;
  private String legalName;
  private String reportName;
  private String email;
  private String address;
  private String contactName;
  private String phone;
  private boolean active = true;

  protected Client() {}

  public Client(
      ParticipantType participantType,
      DocumentType documentType,
      String documentNumber,
      String legalName,
      String reportName,
      String email,
      String address,
      String contactName,
      String phone) {
    update(
        participantType,
        documentType,
        documentNumber,
        legalName,
        reportName,
        email,
        address,
        contactName,
        phone);
  }

  public UUID getId() {
    return id;
  }

  public DocumentType getDocumentType() {
    return documentType;
  }

  public ParticipantType getParticipantType() {
    return participantType;
  }

  public String getDocumentNumber() {
    return documentNumber;
  }

  public String getLegalName() {
    return legalName;
  }

  public String getReportName() {
    return reportName;
  }

  public String getEmail() {
    return email;
  }

  public String getAddress() {
    return address;
  }

  public String getContactName() {
    return contactName;
  }

  public String getPhone() {
    return phone;
  }

  public boolean isActive() {
    return active;
  }

  public void update(
      ParticipantType participantType,
      DocumentType documentType,
      String documentNumber,
      String legalName,
      String reportName,
      String email,
      String address,
      String contactName,
      String phone) {
    this.participantType = participantType;
    this.documentType = documentType;
    this.documentNumber = documentNumber;
    this.legalName = legalName;
    this.reportName = reportName;
    this.email = email;
    this.address = address;
    this.contactName = contactName;
    this.phone = phone;
  }

  public void activate() {
    active = true;
  }

  public void deactivate() {
    active = false;
  }
}
