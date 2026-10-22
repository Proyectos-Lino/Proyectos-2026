package com.odontocare.clinical.controller;

import com.odontocare.clinical.dto.ClinicalContracts.EncounterResponse;
import com.odontocare.clinical.service.EncounterWorkflowService;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/clinical/patients")
public class ClinicalContinuityController {
  private final EncounterWorkflowService workflow;

  public ClinicalContinuityController(EncounterWorkflowService workflow) {
    this.workflow = workflow;
  }

  @GetMapping("/{patientId}/previous-encounter")
  @PreAuthorize("hasAuthority('CLINICAL_READ')")
  public EncounterResponse previous(
      @PathVariable UUID patientId,
      @RequestParam UUID dentistId,
      @RequestParam(required = false) UUID exclude,
      @RequestParam(required = false) UUID planId) {
    return workflow.previous(patientId, dentistId, exclude, planId);
  }
}
