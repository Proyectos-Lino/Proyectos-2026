package com.admision.lector_dbf.importer.model;

import java.util.List;
import java.util.Map;
import java.math.BigDecimal;

public final class ImportModels {
    private ImportModels() {}

    public record Batch(long id, String filename, String status, int total, int valid, int errors) {}

    public record Identification(String sheetCode, String formCode, String participantCode) {}

    public record Response(String sheetCode, String formCode, List<String> answers) {}

    public record Key(String formCode, List<String> answers) {}

    public record Parsed(Map<String, Identification> identifications, List<Response> responses,
                         Map<String, Key> keys) {}

    public record SectionScore(String code, String name, int fromQuestion, int toQuestion,
                               int correct, int incorrect, int blank,
                               BigDecimal correctPoints, BigDecimal incorrectPoints,
                               BigDecimal blankPoints, BigDecimal points) {}

    public record Score(int correct, int incorrect, int blank, BigDecimal points, List<SectionScore> sections) {
        public Score(int correct, int incorrect, int blank, BigDecimal points) {
            this(correct, incorrect, blank, points, List.of());
        }
    }

    public record ImportSummary(long batchId, int total, int valid, int errors) {}
}
