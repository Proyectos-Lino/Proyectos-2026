package com.admision.lector_dbf.report.model;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

public final class AnalyticsModels {
    private AnalyticsModels() {}

    /** groupId=0 representa inscripciones sin grupo; null representa todos. */
    public record Filter(long organizationId, long evaluationId, Long groupId, Long formId) {}

    public record Summary(long registrations, long sheets, long valid, long observed, long annulled,
                          long received, long scored, long passed, long failed, long undecided,
                          BigDecimal average, BigDecimal median, BigDecimal minimum, BigDecimal maximum,
                          BigDecimal deviation) {}

    public record ScoreBin(int index, BigDecimal lower, BigDecimal upper, long count) {}
    public record Dashboard(Summary summary, List<ScoreBin> distribution, OffsetDateTime generatedAt) {}

    public record GroupStats(Long groupId, String code, String name, long registrations, long sheets,
                             long scored, long observed, long annulled, long passed, long failed,
                             long undecided, BigDecimal average, BigDecimal minimum, BigDecimal maximum) {}

    public record QuestionStats(long formId, String formCode, int keyVersion, int question,
                                long responses, long correct, long incorrect, long blank,
                                BigDecimal incorrectPercent) {}

    public record ExportResult(long sheetId, String participantCode, String participantName,
                               String groupName, String formCode, String status, Integer correct,
                               Integer incorrect, Integer blank, BigDecimal score, Integer position,
                               Boolean passed, Integer keyVersion, Integer ruleVersion, Integer scoreVersion) {}
}
