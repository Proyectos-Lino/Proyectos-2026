package com.odontocare.treatments.controller;

import com.odontocare.appointments.dto.AppointmentResponse;
import com.odontocare.shared.pagination.*;
import com.odontocare.treatments.dto.ScheduledSessionContracts.*;
import com.odontocare.treatments.dto.TreatmentContracts.Scheduling;
import com.odontocare.treatments.service.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.*;
import java.util.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/treatment-sessions")
public class TreatmentSessionController {
  private final TreatmentCalendarService calendar;
  private final SessionDates dates;

  public TreatmentSessionController(TreatmentCalendarService calendar, SessionDates dates) {
    this.calendar = calendar;
    this.dates = dates;
  }

  @GetMapping
  @PreAuthorize("hasAuthority('APPOINTMENTS_READ')")
  public PageResponse<Session> list(
      @RequestParam(required = false) UUID patientId,
      @RequestParam(required = false) UUID dentistId,
      @RequestParam(required = false) UUID planId,
      @RequestParam(required = false) LocalDate from,
      @RequestParam(required = false) LocalDate to,
      @RequestParam(required = false) String status,
      @RequestParam(defaultValue = "false") boolean undated,
      @Valid @ModelAttribute PageQuery query) {
    return calendar.list(patientId, dentistId, planId, from, to, status, undated, query);
  }

  @GetMapping("/{id}")
  @PreAuthorize("hasAuthority('APPOINTMENTS_READ')")
  public Session get(@PathVariable UUID id) {
    return calendar.get(id);
  }

  @GetMapping("/coordination-summary")
  @PreAuthorize("hasAuthority('APPOINTMENTS_READ')")
  public com.odontocare.treatments.repository.TreatmentPlanningRepository.CoordinationSummary
      coordination(@RequestParam(required = false) UUID dentistId) {
    return calendar.coordination(dentistId);
  }

  public record Preview(@Min(1) @Max(100) int sessions, @Valid Scheduling scheduling) {}

  @PostMapping("/preview")
  @PreAuthorize("hasAuthority('PLANS_WRITE')")
  public List<LocalDate> preview(@Valid @RequestBody Preview request) {
    return dates.calculate(request.scheduling(), request.sessions());
  }

  @PostMapping("/{id}/assign")
  @PreAuthorize("hasAuthority('APPOINTMENTS_WRITE')")
  public AppointmentResponse assign(@PathVariable UUID id, @Valid @RequestBody Assign request) {
    return calendar.assign(id, request);
  }

  @PutMapping("/{id}/date")
  @PreAuthorize("hasAuthority('APPOINTMENTS_WRITE')")
  public Session date(@PathVariable UUID id, @Valid @RequestBody ChangeDate request) {
    return calendar.changeDate(id, request);
  }
}
