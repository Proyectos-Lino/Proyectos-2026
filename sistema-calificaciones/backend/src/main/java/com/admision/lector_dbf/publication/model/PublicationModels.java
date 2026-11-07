package com.admision.lector_dbf.publication.model;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public final class PublicationModels {
    private PublicationModels() {}

    public record Policy(boolean showScore, boolean showPosition, boolean showPassed,
                         boolean showCounts, OffsetDateTime visibleFrom, OffsetDateTime visibleUntil,
                         Long approvedBy, OffsetDateTime approvedAt, Long publishedBy,
                         OffsetDateTime publishedAt, boolean configured) {}

    public record PublicResult(String evaluation, String participantCode, String participantName,
                               String formCode, BigDecimal score, Integer position, Boolean passed,
                               Integer correct, Integer incorrect, Integer blank,String evaluationType) {
        public PublicResult(String evaluation,String participantCode,String participantName,String formCode,BigDecimal score,Integer position,Boolean passed,Integer correct,Integer incorrect,Integer blank) {
            this(evaluation,participantCode,participantName,formCode,score,position,passed,correct,incorrect,blank,"GENERAL");
        }
    }

    public record AccessCandidate(long registrationId, String participantCode,String participantName,String email) {}
    public record ReportContext(long organizationId,long evaluationId,String evaluationCode,String groupName,
                                java.time.LocalDate evaluationDate,String evaluationType,PublicResult result) {}
}
