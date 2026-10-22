package com.odontocare.whatsapp.dto;

import com.odontocare.whatsapp.model.WhatsAppMessage;
import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.*;

public final class WhatsAppContracts {
  private WhatsAppContracts() {}

  public record Connection(
      boolean enabled,
      boolean configured,
      String provider,
      String sender,
      String inboundUrl,
      String statusUrl,
      int allowedParticipantsCount,
      String sendMode,
      boolean testTemplateConfigured,
      List<String> missing,
      boolean agentEnabled) {}

  public record SendRequest(@NotBlank @Size(max = 1600) String body, @NotNull UUID requestKey) {}

  public record TestReplyRequest(@NotNull UUID requestKey) {}

  public record ChatItem(long sequence, Message message) {}

  /** Items always run from oldest to newest; hasMore follows the requested cursor direction. */
  public record ChatPage(List<ChatItem> items, Long before, Long after, boolean hasMore) {}

  public record Message(
      UUID id,
      UUID conversationId,
      String direction,
      String kind,
      String body,
      String providerSid,
      String status,
      Instant createdAt,
      String errorCode,
      String errorMessage,
      int attempts,
      String source) {
    public static Message of(WhatsAppMessage m) {
      return new Message(
          m.id(),
          m.conversationId(),
          m.direction(),
          m.kind(),
          m.body(),
          m.providerSid(),
          m.status(),
          m.createdAt(),
          m.errorCode(),
          m.errorMessage(),
          m.attempts(),
          m.source());
    }
  }
}
