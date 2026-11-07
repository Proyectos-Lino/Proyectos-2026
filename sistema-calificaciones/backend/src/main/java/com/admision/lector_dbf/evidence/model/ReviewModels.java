package com.admision.lector_dbf.evidence.model;

import java.time.OffsetDateTime;

public final class ReviewModels {
    private ReviewModels() {}

    public record Review(long id, long sheetId, String status, String reason,
                         String resolution, OffsetDateTime openedAt, Long assigneeId, String assigneeName,
                         String openedByName,String resolvedByName,OffsetDateTime resolvedAt) {
        public Review(long id,long sheetId,String status,String reason,String resolution,OffsetDateTime openedAt,Long assigneeId,String assigneeName) {
            this(id,sheetId,status,reason,resolution,openedAt,assigneeId,assigneeName,null,null,null);
        }
    }

    public record ReviewCase(long id, long sheetId, String participantCode, String participantName,
                             String sheetCode, String status, String reason, Long assigneeId,
                             String assigneeName, OffsetDateTime openedAt) {}
    public record Assignee(long id, String name, String role) {}
    public record Neighbors(Long previous,Long next) {}

    public record Evidence(long id, long reviewId, String filename, String contentType, long size,
                           String uploadedByName,OffsetDateTime uploadedAt) {}

    public record EvidenceFile(Evidence metadata, byte[] content) {}
}
