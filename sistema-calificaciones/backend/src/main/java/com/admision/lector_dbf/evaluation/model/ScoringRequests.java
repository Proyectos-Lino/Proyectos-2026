package com.admision.lector_dbf.evaluation.model;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.util.List;

public final class ScoringRequests {
    private ScoringRequests() {}

    public record SaveRankingPolicy(@NotBlank String scope, @NotBlank String approvalMode,
                                    @NotBlank String tieBreak, @Min(0) Integer globalCapacity) {}

    public record SectionInput(@NotBlank String code, @NotBlank String name,
                               @Min(1) int fromQuestion, @Min(1) int toQuestion,
                               @NotNull @DecimalMin("0") BigDecimal correct,
                               @NotNull BigDecimal incorrect, @NotNull BigDecimal blank) {}

    public record SaveSections(@NotNull List<@NotNull @Valid SectionInput> sections) {}
}
