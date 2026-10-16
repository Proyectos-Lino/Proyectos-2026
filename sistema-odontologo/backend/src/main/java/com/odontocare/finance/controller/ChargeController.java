package com.odontocare.finance.controller;

import com.odontocare.finance.dto.FinanceContracts.*;
import com.odontocare.finance.service.ChargeLedgerService;
import com.odontocare.shared.pagination.*;
import jakarta.validation.Valid;
import java.util.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/charges")
public class ChargeController {
  private final ChargeLedgerService service;

  public ChargeController(ChargeLedgerService service) {
    this.service = service;
  }

  @GetMapping
  @PreAuthorize("hasAuthority('FINANCES_READ')")
  public PageResponse<EntryResponse> list(
      @RequestParam UUID patientId,
      @RequestParam(required = false) UUID planId,
      @RequestParam(required = false) UUID originalId,
      @RequestParam(required = false) String kind,
      @RequestParam(required = false) String currency,
      @RequestParam(required = false) Boolean closed,
      @Valid @ModelAttribute PageQuery query) {
    return service.list(patientId, planId, originalId, kind, currency, closed, query);
  }

  @GetMapping("/summary")
  @PreAuthorize("hasAuthority('FINANCES_READ')")
  public List<DebtSummary> summary(@RequestParam UUID patientId) {
    return service.summary(patientId);
  }

  @PostMapping("/{id}/adjustments")
  @PreAuthorize("hasAuthority('FINANCES_ADJUST')")
  public EntryResponse adjust(
      @PathVariable UUID id, @Valid @RequestBody AdjustmentRequest request) {
    return service.adjust(id, request);
  }
}
