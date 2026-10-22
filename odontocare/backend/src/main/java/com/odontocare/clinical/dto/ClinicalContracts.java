package com.odontocare.clinical.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;

public final class ClinicalContracts {
  private ClinicalContracts() {}

  public record TemplateRequest(
      @NotBlank @Size(max = 120) String name,
      @NotNull @Pattern(regexp = "ENCOUNTER|BACKGROUND|CONSENT") String kind,
      @NotNull @Size(max = 12000) String content,
      @NotNull Boolean active,
      @NotNull @PositiveOrZero Long version) {}

  public record TemplateResponse(
      UUID id, String name, String kind, String content, Boolean active, long version) {}

  public record Background(
      @NotNull @Size(max = 4000) String antecedents,
      @NotNull @Size(max = 4000) String allergies,
      @NotNull @Size(max = 4000) String medications,
      @NotNull @Size(max = 8000) String anamnesis) {}

  public record ToothMark(
      @NotNull Integer tooth,
      @NotNull @Pattern(regexp = "M|D|V|L|O|TOOTH") String surface,
      @NotNull
          @Pattern(
              regexp =
                  "UNRECORDED|HEALTHY|CARIES|RESTORATION|MISSING|EXTRACTION|CROWN|ROOT_CANAL|OTHER")
          String finding,
      @NotNull @Size(max = 500) String note) {}

  public record Odontogram(
      @NotNull @Size(max = 312) List<@Valid ToothMark> marks,
      @NotNull @Size(max = 4000) String notes) {}

  public record StateRequest(
      @NotNull UUID patientId,
      @NotNull UUID dentistId,
      @NotNull LocalDate recordedOn,
      UUID previousId,
      @NotBlank @Size(max = 500) String reason,
      @Valid Background background,
      @Valid Odontogram odontogram) {}

  public record StateResponse(
      UUID id,
      UUID patientId,
      String kind,
      LocalDate recordedOn,
      UUID previousId,
      Background background,
      Odontogram odontogram,
      String actorName,
      String reason,
      int sequence,
      UUID dentistId,
      String dentistName,
      Instant createdAt) {}

  public record Procedure(
      UUID serviceId,
      @NotBlank @Size(max = 300) String description,
      @NotNull @Min(1) @Max(100) Integer quantity,
      Integer tooth,
      UUID planItemId,
      @DecimalMin("0") @DecimalMax("99999999.99") @Digits(integer = 8, fraction = 2)
          BigDecimal unitPrice) {}

  public record EncounterContent(
      @NotNull @Size(max = 8000) String anamnesis,
      @NotNull @Size(max = 8000) String evolution,
      @NotNull @Size(max = 8000) String diagnoses,
      @NotNull @Size(max = 8000) String indications,
      @NotNull @Size(max = 50) List<@Valid Procedure> procedures) {}

  public record EncounterRequest(
      @NotNull UUID patientId,
      @NotNull UUID dentistId,
      UUID appointmentId,
      @NotNull LocalDate attendedOn,
      @NotBlank @Size(max = 500) String reason,
      @NotNull @Valid EncounterContent content,
      @NotNull @PositiveOrZero Long version) {}

  public record VersionRequest(@NotNull @PositiveOrZero Long version) {}

  public record CorrectionRequest(
      @NotNull @PositiveOrZero Long version,
      @NotBlank @Size(max = 500) String correctionReason,
      @NotNull @Valid EncounterContent content) {}

  public record EncounterResponse(
      UUID id,
      UUID patientId,
      UUID dentistId,
      String dentistName,
      UUID appointmentId,
      LocalDate attendedOn,
      String reason,
      String status,
      int revision,
      long version,
      EncounterContent content) {}

  public record ProcedureSnapshot(
      UUID serviceId,
      String description,
      int quantity,
      Integer tooth,
      String serviceName,
      BigDecimal unitPrice) {}

  public record RevisionResponse(
      UUID id,
      UUID encounterId,
      int number,
      String patientName,
      String patientCode,
      LocalDate birthDate,
      String patientDocument,
      String dentistName,
      LocalDate attendedOn,
      String reason,
      EncounterContent content,
      List<ProcedureSnapshot> procedures,
      String actorName,
      String correctionReason,
      Instant createdAt) {}

  public record StoredEncounter(EncounterContent content, List<ProcedureSnapshot> procedures) {}
}
