package com.odontocare.integrations.model;

import java.util.List;

/** Internal snapshot. Never used as an HTTP response or logged. */
public record IntegrationValues(
    long version,
    boolean whatsappEnabled,
    boolean aiEnabled,
    String kapsoKey,
    String phoneNumberId,
    String sender,
    String webhookSecret,
    String publicBaseUrl,
    boolean restrictParticipants,
    List<String> participants,
    String groqKey,
    String model,
    java.util.UUID accountScope) {
  public IntegrationValues {
    participants = List.copyOf(participants);
  }

  public static IntegrationValues empty(long version) {
    return new IntegrationValues(
        version,
        false,
        false,
        "",
        "",
        "",
        "",
        "",
        true,
        List.of(),
        "",
        "openai/gpt-oss-20b",
        new java.util.UUID(0, 0));
  }

  @Override
  public String toString() {
    return "IntegrationValues[protected]";
  }
}
