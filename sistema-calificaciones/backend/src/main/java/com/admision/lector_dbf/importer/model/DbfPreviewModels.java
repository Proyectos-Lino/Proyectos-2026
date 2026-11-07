package com.admision.lector_dbf.importer.model;

import com.admision.lector_dbf.shared.PageResult;
import java.math.BigDecimal;
import java.util.List;

public final class DbfPreviewModels {
    private DbfPreviewModels() {}

    public record ColumnMapping(String identificationSheet, String identificationForm,
                                String identificationParticipant, String responseSheet,
                                String responseForm, String responseAnswersFrom,
                                String keyForm, String keyAnswersFrom) {}
    public record FileColumns(List<String> columns, int rows, List<List<String>> sample) {}
    public record Inspection(FileColumns identification, FileColumns responses,
                             FileColumns keys, ColumnMapping suggestedMapping) {}
    public record Options(String mode, int questions, int options, BigDecimal correct,
                          BigDecimal incorrect, BigDecimal blank, BigDecimal passingScore) {}
    public record PreviewRow(int row, String sheetCode, String participantCode,
                             String formCode, String decision, List<String> issues) {}
    public record Preview(String fingerprint, int total, int accepted, int observed,
                          int correctionNeeded, List<String> generalIssues,
                          PageResult<PreviewRow> rows) {}

    public record RawRow(int number, List<String> values) {}
    public record DbfTable(List<String> columns, List<RawRow> rows) {}
    public record DbfFiles(DbfTable identification, DbfTable responses, DbfTable keys) {}
    public record IdentificationRow(int number, ImportModels.Identification value) {}
    public record ResponseRow(int number, ImportModels.Response value) {}
    public record KeyRow(int number, ImportModels.Key value) {}
    public record MappedRows(List<IdentificationRow> identifications,
                             List<ResponseRow> responses, List<KeyRow> keys) {}
}
