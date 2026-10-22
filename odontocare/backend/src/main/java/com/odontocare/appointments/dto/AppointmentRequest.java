package com.odontocare.appointments.dto;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public record AppointmentRequest(
    @NotNull UUID patientId,
    @NotNull UUID dentistId,
    UUID serviceId,
    @NotNull @Size(max = 160) String reason,
    @Min(1) @Max(1440) Integer durationMinutes,
    @NotNull LocalDateTime localStart,
    @NotNull @Size(max = 1000) String notes,
    @NotNull UUID requestKey,
    Boolean administrative,
    UUID planItemId,
    UUID scheduledSessionId,
    @DecimalMin("0") @DecimalMax("99999999.99") @Digits(integer = 8, fraction = 2)
        BigDecimal agreedPrice,
    @PositiveOrZero Long scheduledSessionVersion) {
  public AppointmentRequest(
      UUID patientId,
      UUID dentistId,
      UUID serviceId,
      String reason,
      Integer durationMinutes,
      LocalDateTime localStart,
      String notes,
      UUID requestKey) {
    this(
        patientId,
        dentistId,
        serviceId,
        reason,
        durationMinutes,
        localStart,
        notes,
        requestKey,
        null,
        null,
        null,
        null,
        null);
  }
}
