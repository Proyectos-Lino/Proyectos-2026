package com.odontocare.treatments.dto;

import jakarta.validation.constraints.*;
import java.time.*;
import java.util.*;

public final class ScheduledSessionContracts {
  private ScheduledSessionContracts() {}

  public record Session(
      UUID id,
      UUID planId,
      String planCode,
      String planTitle,
      UUID itemId,
      String description,
      Integer tooth,
      UUID serviceId,
      UUID patientId,
      String patientName,
      UUID dentistId,
      String dentistName,
      int sessionNumber,
      int totalSessions,
      LocalDate plannedOn,
      int durationMinutes,
      String status,
      UUID appointmentId,
      long version,
      String label) {}

  public record Assign(
      @NotNull LocalDateTime localStart,
      @NotNull @PositiveOrZero Long version,
      @NotNull UUID requestKey) {}

  public record ChangeDate(
      @NotNull LocalDate plannedOn,
      @NotNull @PositiveOrZero Long version,
      @NotBlank @Size(max = 500) String reason) {}
}
