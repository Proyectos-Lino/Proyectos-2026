package com.odontocare.patients.model;

import com.odontocare.shared.model.VersionedEntity;
import jakarta.persistence.*;
import java.time.*;
import java.util.*;

@Entity
@Table(name = "patient")
public class Patient extends VersionedEntity {
  @Column(name = "contact_revision", nullable = false)
  private long contactRevision;

  public void advanceContactRevision() {
    contactRevision++;
  }

  @Column(nullable = false, length = 30)
  private String code;

  @Column(nullable = false, length = 160)
  private String fullName;

  private LocalDate birthDate;

  @Column(nullable = false, length = 20)
  private String documentType = "";

  @Column(nullable = false, length = 40)
  private String documentNumber = "";

  @Column(nullable = false, length = 250)
  private String address = "";

  @Column(nullable = false, length = 160)
  private String email = "";

  @Column(nullable = false, length = 160)
  private String emergencyName = "";

  @Column(nullable = false, length = 20)
  private String emergencyPhone = "";

  @Column(nullable = false, length = 2000)
  private String notes = "";

  @Column(nullable = false)
  private boolean provisional = false;

  @Column(nullable = false)
  private boolean active = true;

  @Column(length = 250)
  private String duplicateKey;

  @OneToMany(mappedBy = "patient", cascade = CascadeType.ALL, orphanRemoval = true)
  private List<PatientContact> contacts = new ArrayList<>();

  public String getCode() {
    return code;
  }

  public void setCode(String value) {
    code = value;
  }

  public String getFullName() {
    return fullName;
  }

  public void setFullName(String value) {
    fullName = value;
  }

  public LocalDate getBirthDate() {
    return birthDate;
  }

  public void setBirthDate(LocalDate value) {
    birthDate = value;
  }

  public String getDocumentType() {
    return documentType;
  }

  public void setDocumentType(String value) {
    documentType = value;
  }

  public String getDocumentNumber() {
    return documentNumber;
  }

  public void setDocumentNumber(String value) {
    documentNumber = value;
  }

  public String getAddress() {
    return address;
  }

  public void setAddress(String value) {
    address = value;
  }

  public String getEmail() {
    return email;
  }

  public void setEmail(String value) {
    email = value;
  }

  public String getEmergencyName() {
    return emergencyName;
  }

  public void setEmergencyName(String value) {
    emergencyName = value;
  }

  public String getEmergencyPhone() {
    return emergencyPhone;
  }

  public void setEmergencyPhone(String value) {
    emergencyPhone = value;
  }

  public String getNotes() {
    return notes;
  }

  public void setNotes(String value) {
    notes = value;
  }

  public boolean getProvisional() {
    return provisional;
  }

  public void setProvisional(boolean value) {
    provisional = value;
  }

  public boolean getActive() {
    return active;
  }

  public void setActive(boolean value) {
    active = value;
  }

  public String getDuplicateKey() {
    return duplicateKey;
  }

  public void setDuplicateKey(String value) {
    duplicateKey = value;
  }

  public List<PatientContact> getContacts() {
    return contacts;
  }

  public void setContacts(List<PatientContact> value) {
    contacts = value;
  }
}
