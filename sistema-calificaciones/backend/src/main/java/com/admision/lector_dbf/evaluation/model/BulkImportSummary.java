package com.admision.lector_dbf.evaluation.model;

public record BulkImportSummary(int created, int updated, int skipped, Long reportId) {
    public BulkImportSummary(int created,int skipped) { this(created,0,skipped,null); }
    public BulkImportSummary(int created,int updated,int skipped) { this(created,updated,skipped,null); }
}
