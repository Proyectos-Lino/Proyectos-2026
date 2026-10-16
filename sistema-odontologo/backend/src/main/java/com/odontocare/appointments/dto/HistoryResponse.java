package com.odontocare.appointments.dto;

import com.odontocare.appointments.model.AppointmentHistory;
import java.time.Instant;
import java.util.UUID;

public record HistoryResponse(
    UUID id,
    String action,
    String previousStatus,
    String status,
    Instant previousStart,
    Instant startsAt,
    Instant endsAt,
    String dentistName,
    int durationMinutes,
    String actorName,
    String reason,
    Instant createdAt) {
  public static HistoryResponse of(AppointmentHistory entry) {
    return new HistoryResponse(
        entry.getId(),
        entry.getAction(),
        entry.getPreviousStatus(),
        entry.getStatus(),
        entry.getPreviousStart(),
        entry.getStartsAt(),
        entry.getEndsAt(),
        entry.getDentistName(),
        entry.getDurationMinutes(),
        entry.getActorName(),
        entry.getReason(),
        entry.getCreatedAt());
  }
}
