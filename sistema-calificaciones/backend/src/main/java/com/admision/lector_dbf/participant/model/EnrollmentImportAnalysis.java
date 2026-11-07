package com.admision.lector_dbf.participant.model;

import com.admision.lector_dbf.participant.model.ParticipantModels.*;
import java.util.List;

public record EnrollmentImportAnalysis(ImportCounts counts, List<Row> rows, String fingerprint) {
    public record Row(ImportRow display, EnrollmentPlan plan) {}
}
