package com.odontocare.treatments.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;

public final class TreatmentContracts {
  private TreatmentContracts() {}

  public record ItemRequest(
      UUID serviceId,
      @NotBlank @Size(max = 300) String description,
      Integer tooth,
      @NotNull @Min(1) @Max(100) Integer quantity,
      @NotNull @Min(1) @Max(100) Integer sessions,
      @NotNull @DecimalMin("0") @DecimalMax("99999999.99") @Digits(integer = 8, fraction = 2)
          BigDecimal unitPrice,
      @Min(1) @Max(1440) Integer durationMinutes,
      @Valid Scheduling scheduling) {
    public ItemRequest(
        UUID serviceId,
        String description,
        Integer tooth,
        Integer quantity,
        Integer sessions,
        BigDecimal unitPrice) {
      this(serviceId, description, tooth, quantity, sessions, unitPrice, null, null);
    }
  }

  public record Scheduling(
      @Pattern(regexp = "NONE|DATES|FREQUENCY") String mode,
      @Size(max = 100) List<LocalDate> dates,
      LocalDate firstDate,
      @Min(1) @Max(365) Integer interval,
      @Pattern(regexp = "DAYS|WEEKS|MONTHS") String unit) {
    public static Scheduling none() {
      return new Scheduling("NONE", List.of(), null, null, null);
    }
  }

  public record AgreedInstallment(
      @NotNull LocalDate dueOn,
      @NotNull @DecimalMin("0.01") @Digits(integer = 10, fraction = 2) BigDecimal amount) {}

  public record PaymentAgreement(
      @NotNull @DecimalMin("0") @Digits(integer = 10, fraction = 2) BigDecimal initialAmount,
      @NotNull @Size(max = 59) List<@Valid AgreedInstallment> installments) {
    public static PaymentAgreement none() {
      return new PaymentAgreement(BigDecimal.ZERO, List.of());
    }
  }

  public record InitialCollection(
      @NotNull Boolean received,
      @NotNull LocalDate occurredOn,
      @NotBlank @Pattern(regexp = "CASH|TRANSFER|CARD|OTHER") String method,
      @NotNull @Size(max = 160) String reference) {}

  public record PlanRequest(
      @NotNull UUID patientId,
      @NotNull UUID dentistId,
      @NotBlank @Size(max = 160) String title,
      @NotNull @Size(max = 4000) String conditions,
      @NotNull @Size(min = 1, max = 50) List<@Valid ItemRequest> items,
      @NotNull @PositiveOrZero Long version,
      @NotNull UUID requestKey,
      @Valid PaymentAgreement paymentAgreement) {
    public PlanRequest(
        UUID patientId,
        UUID dentistId,
        String title,
        String conditions,
        List<ItemRequest> items,
        Long version,
        UUID requestKey) {
      this(patientId, dentistId, title, conditions, items, version, requestKey, null);
    }
  }

  public record ActionRequest(
      @NotNull @PositiveOrZero Long version,
      @NotNull UUID requestKey,
      @NotBlank @Size(max = 500) String reason,
      @Size(max = 160) String acceptedBy,
      Boolean releaseUnperformed,
      @Valid InitialCollection initialCollection) {
    public ActionRequest(
        Long version,
        UUID requestKey,
        String reason,
        String acceptedBy,
        Boolean releaseUnperformed) {
      this(version, requestKey, reason, acceptedBy, releaseUnperformed, null);
    }
  }

  public record AdditionalRequest(
      @NotNull @PositiveOrZero Long version,
      @NotNull UUID requestKey,
      @NotBlank @Size(max = 500) String reason,
      @NotNull @Valid ItemRequest item) {}

  public record PlanResponse(
      UUID id,
      UUID patientId,
      UUID dentistId,
      String dentistName,
      String patientName,
      String code,
      String title,
      String conditions,
      String currency,
      String status,
      String acceptedBy,
      Instant acceptedAt,
      BigDecimal originalTotal,
      BigDecimal currentDebt,
      long completedSessions,
      long totalSessions,
      long version,
      PaymentAgreement paymentAgreement,
      UUID initialPaymentId) {}

  public record ItemResponse(
      UUID id,
      UUID planId,
      UUID serviceId,
      String serviceName,
      String description,
      Integer tooth,
      int quantity,
      int sessions,
      BigDecimal unitPrice,
      BigDecimal amount,
      long completedSessions,
      BigDecimal currentDebt,
      String label,
      int durationMinutes,
      Scheduling scheduling) {}

  public record SessionResponse(
      UUID id,
      UUID itemId,
      UUID encounterId,
      String description,
      int sessions,
      String actorName,
      Instant createdAt) {}

  public record OperationResponse(
      UUID id,
      UUID planId,
      String action,
      String reason,
      String actorName,
      String summary,
      Instant createdAt) {}
}
