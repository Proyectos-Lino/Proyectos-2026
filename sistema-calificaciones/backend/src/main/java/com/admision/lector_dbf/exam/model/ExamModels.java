package com.admision.lector_dbf.exam.model;

import java.util.List;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import com.admision.lector_dbf.importer.model.ImportModels.SectionScore;

public final class ExamModels {
    private ExamModels() {}

    public record Exam(long id, long evaluationId, String sheetCode, String participantCode,
                       String participantName, String groupName, String formCode, String status,
                       String observation, Integer correct, Integer incorrect, Integer blank,
                       BigDecimal score, Integer position, Boolean passed, OffsetDateTime createdAt,Long groupId,
                       BigDecimal officialScore, String officialFormula) {
        public BigDecimal rankingScore() { return officialScore == null ? score : officialScore; }
    }

    public record Page<T>(List<T> items, int page, int size, long total, int totalPages) {}

    public record ExplanationData(long sheetId, String formCode, int questions, int keyVersion,
                                  int ruleVersion, BigDecimal correctPoints, BigDecimal incorrectPoints,
                                  BigDecimal blankPoints, BigDecimal minimumScore, Integer sectionVersion,
                                  BigDecimal score, String sectionDetails, BigDecimal officialScore, String officialFormula) {}

    public record Explanation(long sheetId, String formCode, int questions, int keyVersion,
                              int ruleVersion, BigDecimal correctPoints, BigDecimal incorrectPoints,
                              BigDecimal blankPoints, BigDecimal minimumScore, Integer sectionVersion,
                              BigDecimal score, List<SectionScore> sections, BigDecimal officialScore, String officialFormula) {}

    public record RankingDecision(long id, int policyVersion, String scope, String approvalMode,
                                  String tieBreak, Integer capacity, BigDecimal minimumScore,
                                  Integer position, Boolean passed, OffsetDateTime recordedAt) {}
}
