package com.admision.lector_dbf.publication.model;

import jakarta.validation.constraints.NotBlank;
import java.time.OffsetDateTime;

public final class PublicationRequests {
    private PublicationRequests() {}
    public record UpdatePolicy(boolean showScore, boolean showPosition, boolean showPassed,
                               boolean showCounts, OffsetDateTime visibleFrom, OffsetDateTime visibleUntil) {}
    public record Lookup(@NotBlank String accessCode) {}
}
