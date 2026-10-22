package com.odontocare.schedules.dto;

import com.odontocare.schedules.model.PeriodKind;
import java.util.UUID;

public record PeriodResponse(
    UUID id,
    UUID dentistId,
    String dentistName,
    int dayOfWeek,
    PeriodKind kind,
    int startMinute,
    int endMinute,
    boolean active,
    long version) {}
