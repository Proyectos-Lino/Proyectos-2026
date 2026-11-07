package com.admision.lector_dbf.evidence.model;

import jakarta.validation.constraints.*;

public final class ReviewRequests {
    private ReviewRequests() {}

    public record OpenReview(@NotBlank String reason) {}

    public record ResolveReview(@NotBlank String decision, @NotBlank String explanation) {}
    public record AssignReview(Long userId) {}
}
