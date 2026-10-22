package com.odontocare.agent.dto;

import jakarta.validation.constraints.*;
import java.time.*;
import java.util.*;

public final class AgentContracts {
  private AgentContracts() {}

  public record Configuration(
      boolean enabled,
      boolean configured,
      boolean workerEnabled,
      String provider,
      String model,
      String responseMode,
      List<String> missing,
      int maxModelCalls,
      int maxCompletionTokens) {}

  public record TestMessage(
      @NotBlank @Pattern(regexp = "\\+[1-9][0-9]{7,14}") String phone,
      @NotNull @Size(max = 120) String contactName,
      @NotBlank @Size(max = 4096) String body,
      @NotNull UUID requestKey) {}

  public record TestResult(UUID conversationId, UUID runId) {}

  public record Proposal(
      UUID id,
      UUID runId,
      UUID conversationId,
      UUID slotId,
      UUID patientId,
      String patientName,
      String summary,
      String confirmationCode,
      String state,
      UUID appointmentId,
      UUID confirmationMessageId,
      Instant createdAt,
      Instant expiresAt) {}

  public record Slot(
      UUID id,
      UUID runId,
      UUID conversationId,
      UUID dentistId,
      UUID serviceId,
      LocalDateTime localStart,
      int durationMinutes,
      String timeZone,
      Instant expiresAt,
      String serviceName,
      String dentistName) {}

  public record Step(
      UUID id,
      int ordinal,
      String kind,
      String name,
      Object arguments,
      Object result,
      String state,
      Instant createdAt) {}
}
