package com.odontocare.appointments.model;

import com.odontocare.catalog.model.DentalService;
import com.odontocare.dentists.model.Dentist;
import com.odontocare.patients.model.Patient;
import com.odontocare.shared.model.VersionedEntity;
import jakarta.persistence.*;
import java.time.*;
import java.util.*;

@Entity
@Table(name = "appointment")
public class Appointment extends VersionedEntity {
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "patient_id", nullable = false)
  private Patient patient;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "dentist_id", nullable = false)
  private Dentist dentist;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "service_id")
  private DentalService service;

  @Column(nullable = false, length = 300)
  private String serviceName;

  @Column(nullable = false, length = 120)
  private String dentistName;

  @Column(nullable = false)
  private int durationMinutes;

  @Column(nullable = false)
  private int gapMinutes;

  @Column(nullable = false)
  private Instant startsAt;

  @Column(nullable = false)
  private Instant endsAt;

  @Column(nullable = false)
  private Instant blockedUntil;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private AppointmentStatus status = AppointmentStatus.RESERVED;

  @Column(nullable = false, length = 16)
  private String origin = "MANUAL";

  @Column(nullable = false, length = 1000)
  private String notes = "";

  @Column(nullable = false, unique = true)
  private UUID requestKey;

  @Column(nullable = false, length = 64)
  private String requestFingerprint;

  private UUID planItemId;
  private UUID scheduledSessionId;

  @Column(precision = 10, scale = 2)
  private java.math.BigDecimal agreedPrice;

  private boolean administrative;

  @Column(length = 220)
  private String treatmentLabel;

  private Integer treatmentSessionNumber;

  public UUID getPlanItemId() {
    return planItemId;
  }

  public void setPlanItemId(UUID value) {
    planItemId = value;
  }

  public UUID getScheduledSessionId() {
    return scheduledSessionId;
  }

  public void setScheduledSessionId(UUID value) {
    scheduledSessionId = value;
  }

  public java.math.BigDecimal getAgreedPrice() {
    return agreedPrice;
  }

  public void setAgreedPrice(java.math.BigDecimal value) {
    agreedPrice = value;
  }

  public boolean getAdministrative() {
    return administrative;
  }

  public void setAdministrative(boolean value) {
    administrative = value;
  }

  public String getTreatmentLabel() {
    return treatmentLabel;
  }

  public void setTreatmentLabel(String value) {
    treatmentLabel = value;
  }

  public Integer getTreatmentSessionNumber() {
    return treatmentSessionNumber;
  }

  public void setTreatmentSessionNumber(Integer value) {
    treatmentSessionNumber = value;
  }

  public Patient getPatient() {
    return patient;
  }

  public void setPatient(Patient value) {
    patient = value;
  }

  public Dentist getDentist() {
    return dentist;
  }

  public void setDentist(Dentist value) {
    dentist = value;
  }

  public DentalService getService() {
    return service;
  }

  public void setService(DentalService value) {
    service = value;
  }

  public String getServiceName() {
    return serviceName;
  }

  public void setServiceName(String value) {
    serviceName = value;
  }

  public String getDentistName() {
    return dentistName;
  }

  public void setDentistName(String value) {
    dentistName = value;
  }

  public int getDurationMinutes() {
    return durationMinutes;
  }

  public void setDurationMinutes(int value) {
    durationMinutes = value;
  }

  public int getGapMinutes() {
    return gapMinutes;
  }

  public void setGapMinutes(int value) {
    gapMinutes = value;
  }

  public Instant getStartsAt() {
    return startsAt;
  }

  public void setStartsAt(Instant value) {
    startsAt = value;
  }

  public Instant getEndsAt() {
    return endsAt;
  }

  public void setEndsAt(Instant value) {
    endsAt = value;
  }

  public Instant getBlockedUntil() {
    return blockedUntil;
  }

  public void setBlockedUntil(Instant value) {
    blockedUntil = value;
  }

  public AppointmentStatus getStatus() {
    return status;
  }

  public void setStatus(AppointmentStatus value) {
    status = value;
  }

  public String getOrigin() {
    return origin;
  }

  public void setOrigin(String value) {
    origin = value;
  }

  public String getNotes() {
    return notes;
  }

  public void setNotes(String value) {
    notes = value;
  }

  public UUID getRequestKey() {
    return requestKey;
  }

  public void setRequestKey(UUID value) {
    requestKey = value;
  }

  public String getRequestFingerprint() {
    return requestFingerprint;
  }

  public void setRequestFingerprint(String value) {
    requestFingerprint = value;
  }
}
