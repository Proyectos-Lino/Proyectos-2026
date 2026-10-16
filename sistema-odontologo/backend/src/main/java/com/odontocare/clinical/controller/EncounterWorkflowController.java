package com.odontocare.clinical.controller;

import com.odontocare.clinical.dto.EncounterWorkflowContracts.*;
import com.odontocare.clinical.service.EncounterWorkflowService;
import com.odontocare.finance.service.EncounterCheckoutService;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/clinical")
public class EncounterWorkflowController {
  private final EncounterWorkflowService workflow;
  private final EncounterCheckoutService checkout;

  public EncounterWorkflowController(
      EncounterWorkflowService workflow, EncounterCheckoutService checkout) {
    this.workflow = workflow;
    this.checkout = checkout;
  }

  @GetMapping("/from-appointment/{id}")
  @PreAuthorize("hasAuthority('CLINICAL_WRITE') and hasAuthority('APPOINTMENTS_READ')")
  public Start start(@PathVariable UUID id) {
    return workflow.fromAppointment(id);
  }

  @GetMapping("/encounters/{id}/checkout")
  @PreAuthorize(
      "hasAuthority('CLINICAL_WRITE') and hasAuthority('PAYMENTS_WRITE') and"
          + " hasAuthority('FINANCES_READ')")
  public CheckoutPreview preview(@PathVariable UUID id) {
    return checkout.preview(id);
  }

  @PostMapping("/encounters/{id}/checkout")
  @PreAuthorize(
      "hasAuthority('CLINICAL_WRITE') and hasAuthority('PAYMENTS_WRITE') and"
          + " hasAuthority('FINANCES_READ')")
  public CheckoutResult checkout(@PathVariable UUID id, @Valid @RequestBody CheckoutRequest r) {
    return checkout.checkout(id, r);
  }
}
