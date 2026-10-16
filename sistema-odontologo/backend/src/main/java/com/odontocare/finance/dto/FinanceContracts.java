package com.odontocare.finance.dto;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public final class FinanceContracts {
  private FinanceContracts() {}

  public record AdjustmentRequest(
      @NotNull UUID requestKey,
      @NotNull
          @DecimalMin("-9999999999.99")
          @DecimalMax("9999999999.99")
          @Digits(integer = 10, fraction = 2)
          BigDecimal amount,
      @NotBlank @Size(max = 500) String reason) {}

  public record EntryResponse(
      UUID id,
      UUID patientId,
      UUID planId,
      UUID itemId,
      UUID encounterId,
      UUID originalId,
      String kind,
      String description,
      String currency,
      BigDecimal amount,
      BigDecimal unitPrice,
      Integer quantity,
      String reason,
      String actorName,
      Instant createdAt,
      boolean closed,
      BigDecimal pending,
      BigDecimal debt,
      BigDecimal applied,
      String paymentStatus) {}

  public record DebtSummary(
      String currency, BigDecimal charges, BigDecimal adjustments, BigDecimal netDebt) {}
}
