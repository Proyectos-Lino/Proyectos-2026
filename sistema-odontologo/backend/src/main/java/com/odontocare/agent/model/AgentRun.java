package com.odontocare.agent.model;

import java.time.Instant;
import java.util.UUID;

public record AgentRun(
    UUID id,
    UUID messageId,
    UUID conversationId,
    String state,
    String model,
    String responseText,
    String errorCode,
    String errorMessage,
    int attempts,
    int inputTokens,
    int outputTokens,
    Instant createdAt,
    Instant updatedAt) {}
