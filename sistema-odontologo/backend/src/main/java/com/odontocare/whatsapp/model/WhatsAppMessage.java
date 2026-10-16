package com.odontocare.whatsapp.model;

import java.time.Instant;
import java.util.UUID;

public record WhatsAppMessage(
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
    UUID requestKey,
    String templateSid,
    String source) {}
