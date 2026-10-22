package com.odontocare.whatsapp.model;

import java.time.Instant;
import java.util.UUID;

public record WhatsAppConversation(
    UUID id,
    String phone,
    String contactName,
    Instant lastMessageAt,
    Instant lastInboundAt,
    String lastMessagePreview) {}
