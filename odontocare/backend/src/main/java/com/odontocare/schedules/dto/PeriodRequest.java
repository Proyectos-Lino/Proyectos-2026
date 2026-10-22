package com.odontocare.schedules.dto;

import com.odontocare.schedules.model.PeriodKind;
import jakarta.validation.constraints.*;
import java.util.UUID;

public record PeriodRequest(
    @NotNull UUID dentistId,
    @NotNull @Min(1) @Max(7) Integer dayOfWeek,
    @NotNull PeriodKind kind,
    @NotNull @Min(0) @Max(1439) Integer startMinute,
    @NotNull @Min(1) @Max(1440) Integer endMinute,
    @NotNull Boolean active,
    @PositiveOrZero Long version) {}
