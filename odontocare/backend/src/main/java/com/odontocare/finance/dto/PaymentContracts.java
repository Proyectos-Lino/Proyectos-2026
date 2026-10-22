package com.odontocare.finance.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;

public final class PaymentContracts {
  private PaymentContracts() {}

  public record ChargeStateRequest(
      @NotNull UUID requestKey, @NotBlank @Size(max = 500) String reason) {}

  public record Allocation(
      @NotNull UUID chargeId,
      @NotNull @DecimalMin("0.01") @DecimalMax("9999999999.99") @Digits(integer = 10, fraction = 2)
          BigDecimal amount) {}

  public record PaymentRequest(
      @NotNull UUID requestKey,
      @NotNull UUID patientId,
      @NotNull @DecimalMin("0.01") @DecimalMax("9999999999.99") @Digits(integer = 10, fraction = 2)
          BigDecimal amount,
      @NotNull LocalDate occurredOn,
      @NotBlank @Pattern(regexp = "[A-Z]{3}") String currency,
      @NotBlank @Pattern(regexp = "CASH|TRANSFER|CARD|OTHER") String method,
      @NotNull @Size(max = 160) String reference,
      @NotBlank @Size(max = 300) String description,
      @NotNull @Size(max = 50) List<@Valid Allocation> allocations) {}

  public record ApplyRequest(
      @NotNull UUID requestKey,
      @NotBlank @Size(max = 500) String reason,
      @NotNull @Size(min = 1, max = 50) List<@Valid Allocation> allocations) {}

  public record CorrectionRequest(
      @NotNull UUID requestKey,
      @NotNull @DecimalMin("0.01") @DecimalMax("9999999999.99") @Digits(integer = 10, fraction = 2)
          BigDecimal amount,
      @NotNull LocalDate occurredOn,
      @NotBlank @Size(max = 500) String reason,
      @NotNull @Size(max = 50) List<@Valid Allocation> releases) {}

  public record ExpenseRequest(
      @NotNull UUID requestKey,
      @NotNull UUID categoryId,
      @NotNull @DecimalMin("0.01") @DecimalMax("9999999999.99") @Digits(integer = 10, fraction = 2)
          BigDecimal amount,
      @NotNull LocalDate occurredOn,
      @NotBlank @Pattern(regexp = "[A-Z]{3}") String currency,
      @NotBlank @Pattern(regexp = "CASH|TRANSFER|CARD|OTHER") String method,
      @NotNull @Size(max = 160) String reference,
      @NotBlank @Size(max = 300) String description,
      @NotNull @Size(max = 160) String supplier) {}

  public record CategoryRequest(
      Long version, @NotBlank @Size(max = 120) String name, @NotNull Boolean active) {}

  public record CategoryResponse(UUID id, long version, String name, boolean active) {}

  public record OpenCashRequest(
      @NotNull UUID requestKey,
      @NotBlank @Pattern(regexp = "[A-Z]{3}") String currency,
      @NotNull @DecimalMin("0") @DecimalMax("9999999999.99") @Digits(integer = 10, fraction = 2)
          BigDecimal opening,
      @NotBlank @Size(max = 500) String reason) {}

  public record CloseCashRequest(
      @NotNull UUID requestKey,
      @NotNull @DecimalMin("0") @DecimalMax("9999999999.99") @Digits(integer = 10, fraction = 2)
          BigDecimal counted,
      @NotBlank @Size(max = 500) String reason) {}

  public record CashResponse(
      UUID id,
      String currency,
      BigDecimal opening,
      BigDecimal expected,
      BigDecimal counted,
      BigDecimal difference,
      BigDecimal nonCashNet,
      String openedBy,
      String closedBy,
      Instant openedAt,
      Instant closedAt,
      String reason) {}

  public record MovementResponse(
      UUID id,
      UUID patientId,
      UUID originalId,
      UUID cashSessionId,
      String kind,
      String currency,
      BigDecimal amount,
      BigDecimal effectiveAmount,
      BigDecimal applied,
      BigDecimal available,
      BigDecimal closedApplied,
      LocalDate occurredOn,
      String method,
      String reference,
      String description,
      String supplier,
      String categoryName,
      String actorName,
      String receiptCode,
      Instant createdAt) {}

  public record AccountSummary(
      String currency,
      BigDecimal debt,
      BigDecimal received,
      BigDecimal applied,
      BigDecimal pending,
      BigDecimal advance) {}

  public record ApplicationResponse(
      UUID id,
      UUID paymentId,
      UUID chargeId,
      UUID operationId,
      BigDecimal amount,
      String description,
      String reason,
      Instant createdAt) {}

  public record OpenCharge(
      UUID id,
      String description,
      String currency,
      BigDecimal debt,
      BigDecimal applied,
      BigDecimal pending,
      boolean closed) {}

  public record DueRequest(
      @NotNull LocalDate dueOn,
      @NotNull @DecimalMin("0.01") @DecimalMax("9999999999.99") @Digits(integer = 10, fraction = 2)
          BigDecimal amount) {}

  public record ScheduleRequest(
      @NotNull UUID requestKey,
      @NotNull UUID chargeId,
      @NotBlank @Size(max = 500) String reason,
      @NotNull @Size(min = 1, max = 60) List<@Valid DueRequest> installments) {}

  public record DueResponse(
      UUID id,
      UUID chargeId,
      UUID scheduleId,
      int position,
      LocalDate dueOn,
      String currency,
      BigDecimal amount,
      BigDecimal paid,
      BigDecimal pending,
      String status,
      boolean active,
      String reason) {}

  public record SupportRequest(
      @NotNull UUID movementId, @NotBlank @Size(max = 300) String description) {}

  public record DocumentResponse(
      UUID id,
      UUID movementId,
      UUID patientId,
      String fileName,
      String mediaType,
      long byteSize,
      String sha256,
      String description,
      String actorName,
      boolean generated,
      Instant createdAt) {}

  public record Download(String fileName, String mediaType, byte[] bytes) {}

  public record StatementRequest(
      @NotNull UUID requestKey,
      @NotNull UUID patientId,
      @NotBlank @Pattern(regexp = "[A-Z]{3}") String currency) {}

  public record DiscountRequest(
      @NotNull UUID requestKey,
      @NotNull @DecimalMin("0.01") @DecimalMax("9999999999.99") @Digits(integer = 10, fraction = 2)
          BigDecimal amount,
      @NotBlank @Size(max = 500) String reason) {}
}
