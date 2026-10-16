package com.odontocare.schedules.controller;

import com.odontocare.schedules.dto.*;
import com.odontocare.schedules.model.*;
import com.odontocare.schedules.service.*;
import com.odontocare.shared.pagination.*;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/schedules")
public class ScheduleController {
  private final WeeklyPeriodService periods;
  private final ScheduleExceptionService exceptions;

  public ScheduleController(WeeklyPeriodService periods, ScheduleExceptionService exceptions) {
    this.periods = periods;
    this.exceptions = exceptions;
  }

  @GetMapping("/periods")
  @PreAuthorize("hasAuthority('SCHEDULES_READ')")
  public PageResponse<PeriodResponse> periods(
      @Valid @ModelAttribute PageQuery query,
      @RequestParam(required = false) Boolean active,
      @RequestParam(required = false) UUID dentistId,
      @RequestParam(required = false) Integer dayOfWeek,
      @RequestParam(required = false) PeriodKind kind) {
    return periods.list(query, active, dentistId, dayOfWeek, kind);
  }

  @PostMapping("/periods")
  @ResponseStatus(HttpStatus.CREATED)
  @PreAuthorize("hasAuthority('SCHEDULES_WRITE')")
  public PeriodResponse createPeriod(@Valid @RequestBody PeriodRequest request) {
    return periods.create(request);
  }

  @PutMapping("/periods/{id}")
  @PreAuthorize("hasAuthority('SCHEDULES_WRITE')")
  public PeriodResponse updatePeriod(
      @PathVariable UUID id, @Valid @RequestBody PeriodRequest request) {
    return periods.update(id, request);
  }

  @GetMapping("/exceptions")
  @PreAuthorize("hasAuthority('SCHEDULES_READ')")
  public PageResponse<ExceptionResponse> exceptions(
      @Valid @ModelAttribute PageQuery query,
      @RequestParam(required = false) Boolean active,
      @RequestParam(required = false) UUID dentistId,
      @RequestParam(required = false) ExceptionKind kind,
      @RequestParam(required = false) LocalDate fromDate,
      @RequestParam(required = false) LocalDate toDate) {
    return exceptions.list(query, active, dentistId, kind, fromDate, toDate);
  }

  @PostMapping("/exceptions")
  @ResponseStatus(HttpStatus.CREATED)
  @PreAuthorize("hasAuthority('SCHEDULES_WRITE')")
  public ExceptionResponse createException(@Valid @RequestBody ExceptionRequest request) {
    return exceptions.create(request);
  }

  @PutMapping("/exceptions/{id}")
  @PreAuthorize("hasAuthority('SCHEDULES_WRITE')")
  public ExceptionResponse updateException(
      @PathVariable UUID id, @Valid @RequestBody ExceptionRequest request) {
    return exceptions.update(id, request);
  }
}
