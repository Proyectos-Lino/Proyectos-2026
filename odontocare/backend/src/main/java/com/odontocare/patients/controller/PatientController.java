package com.odontocare.patients.controller;

import com.odontocare.patients.dto.*;
import com.odontocare.patients.service.PatientService;
import com.odontocare.shared.pagination.*;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/patients")
public class PatientController {
  private final PatientService service;

  public PatientController(PatientService service) {
    this.service = service;
  }

  @GetMapping
  @PreAuthorize("hasAuthority('PATIENTS_READ')")
  public PageResponse<PatientResponse> list(
      @Valid @ModelAttribute PageQuery query,
      @RequestParam(required = false) Boolean active,
      @RequestParam(required = false) Boolean provisional,
      @RequestParam(required = false) String phone) {
    return service.list(query, active, provisional, phone);
  }

  @GetMapping("/{id}")
  @PreAuthorize("hasAuthority('PATIENTS_READ')")
  public PatientResponse get(@PathVariable UUID id) {
    return service.get(id);
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  @PreAuthorize("hasAuthority('PATIENTS_WRITE')")
  public PatientResponse create(@Valid @RequestBody PatientRequest request) {
    return service.create(request);
  }

  @PutMapping("/{id}")
  @PreAuthorize("hasAuthority('PATIENTS_WRITE')")
  public PatientResponse update(@PathVariable UUID id, @Valid @RequestBody PatientRequest request) {
    return service.update(id, request);
  }
}
