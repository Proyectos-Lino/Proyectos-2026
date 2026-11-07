package com.admision.lector_dbf.evaluation.model;

import java.math.BigDecimal;
import java.util.List;

public final class ScoringModels {
    private ScoringModels() {}

    public record RankingPolicy(long id, long evaluationId, int version, String scope,
                                String approvalMode, String tieBreak, Integer globalCapacity) {}

    public record Section(long id, int order, String code, String name, int fromQuestion,
                          int toQuestion, BigDecimal correct, BigDecimal incorrect, BigDecimal blank) {}

    public record SectionConfiguration(long id, long formId, int version, List<Section> sections) {}
}
