package com.odontocare.schedules.dto;

import com.odontocare.schedules.model.ExceptionKind;
import java.time.LocalDate;
import java.util.UUID;

public record ExceptionResponse(
    UUID id,
    UUID dentistId,
    String dentistName,
    ExceptionKind kind,
    LocalDate startDate,
    LocalDate endDate,
    Integer startMinute,
    Integer endMinute,
    String reason,
    boolean active,
    long version) {}
