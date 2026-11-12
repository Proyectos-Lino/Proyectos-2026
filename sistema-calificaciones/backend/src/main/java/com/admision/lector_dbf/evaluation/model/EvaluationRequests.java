package com.admision.lector_dbf.evaluation.model;

import java.util.List;
import java.math.BigDecimal;
import java.time.LocalDate;
import jakarta.validation.constraints.*;

public final class EvaluationRequests {
    private EvaluationRequests() {}

    public record CreateEvaluation(@NotBlank String code, @NotBlank String name, String description,
                                   @NotBlank String type, LocalDate date, String groupType) {}

    public record UpdateEvaluation(@NotBlank String name, String description, LocalDate date, String groupType) {}

    public record ChangeStatus(@NotBlank String status) {}

    public record CreateGroup(@NotBlank String code, @NotBlank String name, @Min(0) Integer capacity) {}

    public record CreateForm(@NotBlank String code, @Min(1) int questions, @Min(2) @Max(26) int options) {}

    public record SaveKey(@NotNull List<@NotNull String> answers) {}

    public record SaveRule(@NotNull @DecimalMin("0") BigDecimal correct, @NotNull BigDecimal incorrect,
                           @NotNull BigDecimal blank, BigDecimal passingScore, String officialFormula) {}

    public record PreviewOfficialScore(@NotNull @Digits(integer = 10, fraction = 4) BigDecimal originalScore,
                                       @NotBlank @Size(max = 200) String formula) {}

    public record CreateParticipant(@NotBlank @Size(max=80) String code, @NotBlank @Size(max=160) String names,
                                    @Size(max=160) String surnames, @Size(max=40) String document,
                                    @Email @Size(max=254) String email) {
        public CreateParticipant(String code,String names,String surnames,String document) { this(code,names,surnames,document,null); }
    }

    public record RegisterParticipant(@NotNull Long participantId, Long groupId) {}

    public record UpdateCapacity(@Min(0) Integer capacity) {}

    public record UpdateGroup(@NotBlank String code, @NotBlank String name, @Min(0) Integer capacity) {}

    public record UpdateParticipant(@NotBlank @Size(max=160) String names, @Size(max=160) String surnames,
                                    @Size(max=40) String document, @Email @Size(max=254) String email) {}

    public record UpdateRegistration(Long groupId) {}
}
