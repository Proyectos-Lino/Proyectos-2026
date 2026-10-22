package com.odontocare.schedules.dto;

import com.odontocare.schedules.model.ExceptionKind;
import jakarta.validation.constraints.*;
import java.time.LocalDate;
import java.util.UUID;

public record ExceptionRequest(
    UUID dentistId,
    @NotNull ExceptionKind kind,
    @NotNull LocalDate startDate,
    @NotNull LocalDate endDate,
    @Min(0) @Max(1439) Integer startMinute,
    @Min(1) @Max(1440) Integer endMinute,
    @NotBlank @Size(max = 200) String reason,
    @NotNull Boolean active,
    @PositiveOrZero Long version) {}
