package com.odontocare.treatments.controller;

import com.odontocare.shared.pagination.*;
import com.odontocare.treatments.dto.TreatmentContracts.*;
import com.odontocare.treatments.service.TreatmentPlanService;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/plans")
public class TreatmentPlanController {
  private final TreatmentPlanService service;

  public TreatmentPlanController(TreatmentPlanService service) {
    this.service = service;
  }

  @GetMapping
  @PreAuthorize("hasAuthority('PLANS_READ')")
  public PageResponse<PlanResponse> list(
      @RequestParam(required = false) UUID patientId,
      @RequestParam(required = false) UUID dentistId,
      @RequestParam(required = false) String status,
      @Valid @ModelAttribute PageQuery query) {
    return service.list(patientId, dentistId, status, query);
  }

  @GetMapping("/{id}")
  @PreAuthorize("hasAuthority('PLANS_READ')")
  public PlanResponse get(@PathVariable UUID id) {
    return service.get(id);
  }

  @PostMapping
  @ResponseStatus(org.springframework.http.HttpStatus.CREATED)
  @PreAuthorize("hasAuthority('PLANS_WRITE')")
  public PlanResponse create(@Valid @RequestBody PlanRequest request) {
    return service.save(null, request);
  }

  @PutMapping("/{id}")
  @PreAuthorize("hasAuthority('PLANS_WRITE')")
  public PlanResponse edit(@PathVariable UUID id, @Valid @RequestBody PlanRequest request) {
    return service.save(id, request);
  }

  @PostMapping("/{id}/actions/{action}")
  @PreAuthorize("hasAuthority('PLANS_WRITE')")
  public PlanResponse action(
      @PathVariable UUID id,
      @PathVariable String action,
      @Valid @RequestBody ActionRequest request) {
    return service.action(id, action, request);
  }

  @PostMapping("/{id}/additional")
  @PreAuthorize("hasAuthority('PLANS_WRITE')")
  public PlanResponse additional(
      @PathVariable UUID id, @Valid @RequestBody AdditionalRequest request) {
    return service.additional(id, request);
  }

  @GetMapping("/items")
  @PreAuthorize("hasAuthority('PLANS_READ')")
  public PageResponse<ItemResponse> items(
      @RequestParam(required = false) UUID planId,
      @RequestParam(required = false) UUID patientId,
      @RequestParam(required = false) UUID dentistId,
      @RequestParam(required = false) Boolean available,
      @Valid @ModelAttribute PageQuery query) {
    return service.listItems(planId, patientId, dentistId, available, query);
  }

  @GetMapping("/items/{id}")
  @PreAuthorize("hasAuthority('PLANS_READ')")
  public ItemResponse item(@PathVariable UUID id) {
    return service.getItem(id);
  }

  @GetMapping("/{id}/sessions")
  @PreAuthorize("hasAuthority('PLANS_READ')")
  public PageResponse<SessionResponse> sessions(
      @PathVariable UUID id,
      @RequestParam(required = false) UUID itemId,
      @Valid @ModelAttribute PageQuery query) {
    return service.completedSessions(id, itemId, query);
  }

  @GetMapping("/{id}/history")
  @PreAuthorize("hasAuthority('PLANS_READ')")
  public PageResponse<OperationResponse> history(
      @PathVariable UUID id, @Valid @ModelAttribute PageQuery query) {
    return service.history(id, query);
  }
}
