package com.odontocare.appointments.dto;

import jakarta.validation.constraints.*;
import java.time.LocalDateTime;
import java.util.UUID;

public record RescheduleRequest(
    @NotNull Long version,
    @NotNull UUID dentistId,
    @NotNull LocalDateTime localStart,
    boolean useCurrentDuration,
    @NotBlank @Size(max = 500) String reason) {}
