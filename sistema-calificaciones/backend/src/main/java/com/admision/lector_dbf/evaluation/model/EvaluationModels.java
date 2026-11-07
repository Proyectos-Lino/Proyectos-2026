package com.admision.lector_dbf.evaluation.model;

import java.math.BigDecimal;
import java.time.LocalDate;

public final class EvaluationModels {
    private EvaluationModels() {}

    public record Evaluation(long id, long organizationId, String code, String name,
                             String description, String type, String status, LocalDate date, String groupType) {}

    public record Overview(long id, long organizationId, String code, String name,
                           String description, String type, String status, LocalDate date, String groupType,
                           long participants, long exams, long observed, long admitted, BigDecimal averageScore) {}

    public record Statistics(long evaluations, long exams, long observed, long published) {}

    public record Group(long id, long evaluationId, String code, String name, Integer capacity) {}

    public record Form(long id, long evaluationId, String code, int questions, int options) {}

    public record AnswerKey(long id, long formId, int version, String answers) {}

    public record Rule(long id, long evaluationId, int version, BigDecimal correct,
                       BigDecimal incorrect, BigDecimal blank, BigDecimal passingScore) {}

    public record Participant(long id, long organizationId, String code, String names,
                              String surnames, String document, String email, boolean automatic) {}

    public record ParticipantWrite(long id, boolean created) {}
    public record RegistrationStatus(boolean registered) {}

    public record Registration(long id, long participantId, String code, String names,
                               String surnames, Long groupId, String groupName) {}
}
