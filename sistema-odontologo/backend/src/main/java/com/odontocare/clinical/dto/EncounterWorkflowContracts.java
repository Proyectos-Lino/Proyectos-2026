package com.odontocare.clinical.dto;

import com.odontocare.clinical.dto.ClinicalContracts.*;
import com.odontocare.finance.dto.PaymentContracts.MovementResponse;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

public final class EncounterWorkflowContracts {
  private EncounterWorkflowContracts() {}

  public record Start(EncounterResponse existing, EncounterRequest defaults, String dentistName) {}

  public record CheckoutPreview(
      String currency,
      BigDecimal amount,
      int individualCharges,
      int includedProcedures,
      boolean finalized) {}

  public record CheckoutRequest(
      @NotNull UUID requestKey,
      @NotNull @PositiveOrZero Long version,
      @NotNull @DecimalMin("0") @DecimalMax("9999999999.99") @Digits(integer = 10, fraction = 2)
          BigDecimal amount,
      @NotNull LocalDate occurredOn,
      @NotBlank @Pattern(regexp = "CASH|TRANSFER|CARD|OTHER") String method,
      @NotNull @Size(max = 160) String reference) {}

  public record CheckoutResult(
      EncounterResponse encounter, MovementResponse payment, boolean repeated) {}
}
