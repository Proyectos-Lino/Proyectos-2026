package com.odontocare.integrations.dto;

import jakarta.validation.constraints.*;
import java.util.List;

public final class IntegrationContracts {
  private IntegrationContracts() {}

  public record Save(
      @Min(0) long version,
      boolean whatsappEnabled,
      boolean aiEnabled,
      @Size(max = 512) String kapsoKey,
      boolean clearKapsoKey,
      @NotNull @Size(max = 30) String phoneNumberId,
      @NotNull @Size(max = 20) String sender,
      @Size(max = 512) String webhookSecret,
      boolean clearWebhookSecret,
      @NotNull @Size(max = 250) String publicBaseUrl,
      boolean restrictParticipants,
      @NotNull @Size(max = 100) List<@Pattern(regexp = "\\+[1-9][0-9]{7,14}") String> participants,
      @Size(max = 512) String groqKey,
      boolean clearGroqKey,
      @NotNull @Pattern(regexp = "openai/gpt-oss-(20b|120b)") String model) {
    @Override
    public String toString() {
      return "Save[credentials protected]";
    }
  }

  public record View(
      long version,
      boolean whatsappEnabled,
      boolean aiEnabled,
      boolean hasKapsoKey,
      String phoneNumberId,
      String sender,
      boolean hasWebhookSecret,
      String publicBaseUrl,
      String webhookUrl,
      boolean fixedWebhookUrl,
      boolean restrictParticipants,
      List<String> participants,
      boolean hasGroqKey,
      String model,
      boolean encryptionReady,
      boolean credentialsLocked) {}

  public record Check(@Min(0) long version) {}

  public record ProbeResult(boolean success, String message) {}
}
