package com.admision.lector_dbf.participant.model;

import com.admision.lector_dbf.shared.PageResult;

public final class ParticipantModels {
    private ParticipantModels() {}
    public record Person(long id, String names, String surnames, String documentType, String document,
                         String email, boolean automatic, long registrations) {}
    public record Enrollment(long id, long personId, String code, String names, String surnames,
                             String documentType, String document, String email, boolean automatic,
                             Long groupId, String groupName, boolean hasExam) {}
    public record Summary(long total, long incomplete, long withoutGroup, long withoutEmail) {}
    public record ImportRow(int row, String code, String action, String detail, String previousGroup, String group) {}
    public record ImportCounts(int total, int newEnrollments, int newPeople, int completed,
                               int reused, int preserved, int groupChanges, int errors) {}
    public record Preview(ImportCounts counts, PageResult<ImportRow> rows, String fingerprint) {}
    public record Imported(long reportId, ImportCounts counts) {}
}
