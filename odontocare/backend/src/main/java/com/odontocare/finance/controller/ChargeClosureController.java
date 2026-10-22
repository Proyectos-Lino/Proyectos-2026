package com.odontocare.finance.controller;

import com.odontocare.finance.dto.PaymentContracts.*;
import com.odontocare.finance.service.ChargeClosureService;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/finance/charges")
public class ChargeClosureController {
  private final ChargeClosureService service;

  public ChargeClosureController(ChargeClosureService service) {
    this.service = service;
  }

  @PostMapping("/{id}/close")
  @PreAuthorize("hasAuthority('PAYMENTS_WRITE')")
  public OpenCharge close(@PathVariable UUID id, @Valid @RequestBody ChargeStateRequest request) {
    return service.change(id, request, true);
  }

  @PostMapping("/{id}/reopen")
  @PreAuthorize("hasAuthority('FINANCES_ADJUST')")
  public OpenCharge reopen(@PathVariable UUID id, @Valid @RequestBody ChargeStateRequest request) {
    return service.change(id, request, false);
  }
}
