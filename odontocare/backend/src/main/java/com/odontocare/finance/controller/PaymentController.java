package com.odontocare.finance.controller;

import com.odontocare.finance.dto.PaymentContracts.*;
import com.odontocare.finance.service.*;
import com.odontocare.shared.pagination.*;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/finance")
public class PaymentController {
  private final PaymentService payments;
  private final AccountQueryService queries;
  private final InstallmentService installments;
  private final ChargeLedgerService charges;

  public PaymentController(
      PaymentService payments,
      AccountQueryService queries,
      InstallmentService installments,
      ChargeLedgerService charges) {
    this.payments = payments;
    this.queries = queries;
    this.installments = installments;
    this.charges = charges;
  }

  @GetMapping("/summary")
  @PreAuthorize("hasAuthority('FINANCES_READ')")
  public List<AccountSummary> summary(
      @RequestParam UUID patientId, @RequestParam(defaultValue = "false") boolean historical) {
    return historical ? queries.historicalSummary(patientId) : queries.summary(patientId);
  }

  @GetMapping("/charges/{id}")
  @PreAuthorize("hasAuthority('FINANCES_READ')")
  public OpenCharge charge(@PathVariable UUID id) {
    return queries.charge(id);
  }

  @GetMapping("/charges")
  @PreAuthorize("hasAuthority('FINANCES_READ')")
  public PageResponse<OpenCharge> charges(
      @RequestParam UUID patientId,
      @RequestParam(required = false) String currency,
      @RequestParam(defaultValue = "false") boolean pendingOnly,
      @RequestParam(defaultValue = "false") Boolean closed,
      @RequestParam(required = false) UUID encounterId,
      @Valid @ModelAttribute PageQuery query) {
    return queries.charges(patientId, currency, pendingOnly, closed, encounterId, query);
  }

  @GetMapping("/movements")
  @PreAuthorize("hasAuthority('FINANCES_READ')")
  public PageResponse<MovementResponse> movements(
      @RequestParam(required = false) UUID patientId,
      @RequestParam(required = false) UUID originalId,
      @RequestParam(required = false) UUID cashSessionId,
      @RequestParam(required = false) String kind,
      @RequestParam(required = false) String method,
      @RequestParam(required = false) String currency,
      @RequestParam(required = false) LocalDate from,
      @RequestParam(required = false) LocalDate to,
      @Valid @ModelAttribute PageQuery query) {
    return queries.movements(
        patientId, originalId, cashSessionId, kind, method, currency, from, to, query);
  }

  @GetMapping("/applications")
  @PreAuthorize("hasAuthority('FINANCES_READ')")
  public PageResponse<ApplicationResponse> applications(
      @RequestParam(required = false) UUID paymentId,
      @RequestParam(required = false) UUID chargeId,
      @Valid @ModelAttribute PageQuery query) {
    return queries.applications(paymentId, chargeId, query);
  }

  @PostMapping("/payments")
  @ResponseStatus(HttpStatus.CREATED)
  @PreAuthorize("hasAuthority('PAYMENTS_WRITE')")
  public MovementResponse register(@Valid @RequestBody PaymentRequest r) {
    return payments.register(r);
  }

  @PostMapping("/payments/{id}/apply")
  @PreAuthorize("hasAuthority('PAYMENTS_WRITE')")
  public MovementResponse apply(@PathVariable UUID id, @Valid @RequestBody ApplyRequest r) {
    return payments.apply(id, r, false);
  }

  @PostMapping("/payments/{id}/release")
  @PreAuthorize("hasAuthority('FINANCES_ADJUST')")
  public MovementResponse release(@PathVariable UUID id, @Valid @RequestBody ApplyRequest r) {
    return payments.apply(id, r, true);
  }

  @PostMapping("/payments/{id}/refund")
  @PreAuthorize("hasAuthority('FINANCES_ADJUST')")
  public MovementResponse refund(@PathVariable UUID id, @Valid @RequestBody CorrectionRequest r) {
    return payments.correct(id, r, false);
  }

  @PostMapping("/payments/{id}/reverse")
  @PreAuthorize("hasAuthority('FINANCES_ADJUST')")
  public MovementResponse reverse(@PathVariable UUID id, @Valid @RequestBody CorrectionRequest r) {
    return payments.correct(id, r, true);
  }

  @PostMapping("/charges/{id}/discount")
  @PreAuthorize("hasAuthority('FINANCES_ADJUST')")
  public com.odontocare.finance.dto.FinanceContracts.EntryResponse discount(
      @PathVariable UUID id, @Valid @RequestBody DiscountRequest r) {
    return charges.discount(id, r, false);
  }

  @PostMapping("/charges/{id}/void")
  @PreAuthorize("hasAuthority('FINANCES_ADJUST')")
  public com.odontocare.finance.dto.FinanceContracts.EntryResponse cancel(
      @PathVariable UUID id, @Valid @RequestBody DiscountRequest r) {
    return charges.discount(id, r, true);
  }

  @PostMapping("/installments")
  @ResponseStatus(HttpStatus.CREATED)
  @PreAuthorize("hasAuthority('PAYMENTS_WRITE')")
  public Map<String, UUID> schedule(@Valid @RequestBody ScheduleRequest r) {
    return Map.of("id", installments.schedule(r));
  }

  @GetMapping("/installments")
  @PreAuthorize("hasAuthority('FINANCES_READ')")
  public PageResponse<DueResponse> installments(
      @RequestParam UUID patientId,
      @RequestParam(required = false) UUID chargeId,
      @RequestParam(required = false) Boolean active,
      @RequestParam(required = false) LocalDate from,
      @RequestParam(required = false) LocalDate to,
      @Valid @ModelAttribute PageQuery query) {
    return installments.list(patientId, chargeId, active, from, to, query);
  }
}
