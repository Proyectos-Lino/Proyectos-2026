package com.admision.lector_dbf.participant.model;

import jakarta.validation.constraints.*;

public final class ParticipantRequests {
    private ParticipantRequests() {}
    public record SaveEnrollment(@NotBlank @Size(max=80) String code, Long personId,
            @NotBlank @Size(max=160) String names, @Size(max=160) String surnames,
            @Size(max=30) String documentType, @Size(max=40) String document,
            @Email @Size(max=254) String email, Long groupId) {}
}
