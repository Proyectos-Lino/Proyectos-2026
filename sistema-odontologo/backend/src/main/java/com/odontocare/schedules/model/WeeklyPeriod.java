package com.odontocare.schedules.model;

import com.odontocare.dentists.model.Dentist;
import com.odontocare.shared.model.VersionedEntity;
import jakarta.persistence.*;
import java.util.*;

@Entity
@Table(name = "weekly_period")
public class WeeklyPeriod extends VersionedEntity {
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "dentist_id", nullable = false)
  private Dentist dentist;

  @Column(name = "day_of_week", nullable = false)
  private int dayOfWeek;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 10)
  private PeriodKind kind;

  @Column(name = "start_minute", nullable = false)
  private int startMinute;

  @Column(name = "end_minute", nullable = false)
  private int endMinute;

  @Column(nullable = false)
  private boolean active = true;

  public Dentist getDentist() {
    return dentist;
  }

  public void setDentist(Dentist dentist) {
    this.dentist = dentist;
  }

  public int getDayOfWeek() {
    return dayOfWeek;
  }

  public void setDayOfWeek(int dayOfWeek) {
    this.dayOfWeek = dayOfWeek;
  }

  public PeriodKind getKind() {
    return kind;
  }

  public void setKind(PeriodKind kind) {
    this.kind = kind;
  }

  public int getStartMinute() {
    return startMinute;
  }

  public void setStartMinute(int startMinute) {
    this.startMinute = startMinute;
  }

  public int getEndMinute() {
    return endMinute;
  }

  public void setEndMinute(int endMinute) {
    this.endMinute = endMinute;
  }

  public boolean getActive() {
    return active;
  }

  public void setActive(boolean active) {
    this.active = active;
  }
}
