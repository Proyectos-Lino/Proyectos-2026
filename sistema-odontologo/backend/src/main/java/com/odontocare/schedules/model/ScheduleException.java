package com.odontocare.schedules.model;

import com.odontocare.dentists.model.Dentist;
import com.odontocare.shared.model.VersionedEntity;
import jakarta.persistence.*;
import java.time.LocalDate;
import java.util.*;

@Entity
@Table(name = "schedule_exception")
public class ScheduleException extends VersionedEntity {
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "dentist_id")
  private Dentist dentist;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 10)
  private ExceptionKind kind;

  @Column(name = "start_date", nullable = false)
  private LocalDate startDate;

  @Column(name = "end_date", nullable = false)
  private LocalDate endDate;

  @Column(name = "start_minute")
  private Integer startMinute;

  @Column(name = "end_minute")
  private Integer endMinute;

  @Column(nullable = false, length = 200)
  private String reason;

  @Column(nullable = false)
  private boolean active = true;

  public Dentist getDentist() {
    return dentist;
  }

  public void setDentist(Dentist dentist) {
    this.dentist = dentist;
  }

  public ExceptionKind getKind() {
    return kind;
  }

  public void setKind(ExceptionKind kind) {
    this.kind = kind;
  }

  public LocalDate getStartDate() {
    return startDate;
  }

  public void setStartDate(LocalDate startDate) {
    this.startDate = startDate;
  }

  public LocalDate getEndDate() {
    return endDate;
  }

  public void setEndDate(LocalDate endDate) {
    this.endDate = endDate;
  }

  public Integer getStartMinute() {
    return startMinute;
  }

  public void setStartMinute(Integer startMinute) {
    this.startMinute = startMinute;
  }

  public Integer getEndMinute() {
    return endMinute;
  }

  public void setEndMinute(Integer endMinute) {
    this.endMinute = endMinute;
  }

  public String getReason() {
    return reason;
  }

  public void setReason(String reason) {
    this.reason = reason;
  }

  public boolean getActive() {
    return active;
  }

  public void setActive(boolean active) {
    this.active = active;
  }
}
