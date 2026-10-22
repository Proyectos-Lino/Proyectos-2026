package com.odontocare.agent.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.*;

public final class SupervisionContracts {
  private SupervisionContracts() {}

  public record Period(
      @Min(1) @Max(7) int dayOfWeek,
      @Min(0) @Max(1439) int startMinute,
      @Min(1) @Max(1440) int endMinute) {}

  public record Policy(
      @NotNull Long version,
      boolean enabled,
      @NotNull @Size(max = 28) List<@Valid Period> schedule,
      @Min(0) @Max(43200) int changeLeadMinutes,
      boolean allowReschedule,
      boolean allowCancel,
      @NotBlank @Size(max = 900) String handoffText,
      @NotBlank @Size(max = 900) String clinicalText,
      @NotBlank @Size(max = 900) String closedText,
      @NotBlank @Size(max = 900) String failureText) {}

  public record Control(
      @NotBlank @Pattern(regexp = "HUMAN|AUTO|CLOSED") String mode,
      @NotNull @Size(max = 500) String reason,
      @NotNull Long generation) {}

  public record Context(
      UUID conversationId,
      String mode,
      long generation,
      UUID assignedUserId,
      String assignedName,
      String reason,
      String source,
      String requestState,
      String summary,
      UUID patientId,
      String patientName,
      UUID appointmentId,
      Instant updatedAt) {}

  public record Change(
      UUID id,
      UUID runId,
      UUID conversationId,
      String source,
      String action,
      UUID appointmentId,
      UUID patientId,
      long appointmentVersion,
      UUID slotId,
      String reason,
      String summary,
      String confirmationCode,
      String state,
      Instant createdAt,
      Instant expiresAt) {}
}
