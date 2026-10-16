package com.odontocare.appointments.model;

import com.odontocare.shared.model.VersionedEntity;
import jakarta.persistence.*;
import java.time.*;
import java.util.*;

@Entity
@Table(name = "appointment_history")
public class AppointmentHistory extends VersionedEntity {
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "appointment_id", nullable = false)
  private Appointment appointment;

  @Column(nullable = false, length = 24)
  private String action;

  @Column(length = 20)
  private String previousStatus;

  @Column(nullable = false, length = 20)
  private String status;

  private Instant previousStart;

  @Column(nullable = false)
  private Instant startsAt;

  @Column(nullable = false)
  private Instant endsAt;

  @Column(nullable = false)
  private UUID dentistId;

  @Column(nullable = false, length = 120)
  private String dentistName;

  @Column(nullable = false)
  private int durationMinutes;

  @Column(nullable = false, length = 120)
  private String actorName;

  @Column(nullable = false, length = 500)
  private String reason = "";

  public Appointment getAppointment() {
    return appointment;
  }

  public void setAppointment(Appointment value) {
    appointment = value;
  }

  public String getAction() {
    return action;
  }

  public void setAction(String value) {
    action = value;
  }

  public String getPreviousStatus() {
    return previousStatus;
  }

  public void setPreviousStatus(String value) {
    previousStatus = value;
  }

  public String getStatus() {
    return status;
  }

  public void setStatus(String value) {
    status = value;
  }

  public Instant getPreviousStart() {
    return previousStart;
  }

  public void setPreviousStart(Instant value) {
    previousStart = value;
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

  public int getDurationMinutes() {
    return durationMinutes;
  }

  public void setDurationMinutes(int value) {
    durationMinutes = value;
  }

  public String getActorName() {
    return actorName;
  }

  public void setActorName(String value) {
    actorName = value;
  }

  public String getReason() {
    return reason;
  }

  public void setReason(String value) {
    reason = value;
  }
}
