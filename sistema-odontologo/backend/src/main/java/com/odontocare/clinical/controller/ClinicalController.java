package com.odontocare.clinical.controller;

import com.odontocare.clinical.dto.ClinicalContracts.*;
import com.odontocare.clinical.service.*;
import com.odontocare.shared.pagination.*;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/clinical")
public class ClinicalController {
  private final EncounterService encounters;
  private final ClinicalStateService states;

  public ClinicalController(EncounterService encounters, ClinicalStateService states) {
    this.encounters = encounters;
    this.states = states;
  }

  @GetMapping("/encounters")
  @PreAuthorize("hasAuthority('CLINICAL_READ')")
  public PageResponse<EncounterResponse> list(
      @RequestParam UUID patientId,
      @RequestParam(required = false) String status,
      @RequestParam(required = false) UUID dentistId,
      @Valid @ModelAttribute PageQuery query) {
    return encounters.list(patientId, status, dentistId, query);
  }

  @GetMapping("/encounters/{id}")
  @PreAuthorize("hasAuthority('CLINICAL_READ')")
  public EncounterResponse get(@PathVariable UUID id) {
    return encounters.get(id);
  }

  @PostMapping("/encounters")
  @ResponseStatus(org.springframework.http.HttpStatus.CREATED)
  @PreAuthorize("hasAuthority('CLINICAL_WRITE')")
  public EncounterResponse create(@Valid @RequestBody EncounterRequest request) {
    return encounters.save(null, request);
  }

  @PutMapping("/encounters/{id}")
  @PreAuthorize("hasAuthority('CLINICAL_WRITE')")
  public EncounterResponse update(
      @PathVariable UUID id, @Valid @RequestBody EncounterRequest request) {
    return encounters.save(id, request);
  }

  @PostMapping("/encounters/{id}/finalize")
  @PreAuthorize("hasAuthority('CLINICAL_WRITE')")
  public EncounterResponse finish(
      @PathVariable UUID id, @Valid @RequestBody VersionRequest request) {
    return encounters.finish(id, request);
  }

  @PostMapping("/encounters/{id}/corrections")
  @PreAuthorize("hasAuthority('CLINICAL_WRITE')")
  public EncounterResponse correct(
      @PathVariable UUID id, @Valid @RequestBody CorrectionRequest request) {
    return encounters.correct(id, request);
  }

  @GetMapping("/encounters/{id}/versions")
  @PreAuthorize("hasAuthority('CLINICAL_READ')")
  public PageResponse<RevisionResponse> history(
      @PathVariable UUID id, @Valid @ModelAttribute PageQuery query) {
    return encounters.history(id, query);
  }

  @GetMapping("/states/{kind}")
  @PreAuthorize("hasAuthority('CLINICAL_READ')")
  public PageResponse<StateResponse> states(
      @PathVariable String kind,
      @RequestParam UUID patientId,
      @Valid @ModelAttribute PageQuery query) {
    return states.list(patientId, kind, query);
  }

  @PostMapping("/states/{kind}")
  @ResponseStatus(org.springframework.http.HttpStatus.CREATED)
  @PreAuthorize("hasAuthority('CLINICAL_WRITE')")
  public StateResponse state(@PathVariable String kind, @Valid @RequestBody StateRequest request) {
    return states.create(kind, request);
  }
}
