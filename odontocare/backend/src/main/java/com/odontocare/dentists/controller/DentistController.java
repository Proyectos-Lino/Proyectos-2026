package com.odontocare.dentists.controller;

import com.odontocare.dentists.dto.*;
import com.odontocare.dentists.service.DentistService;
import com.odontocare.shared.pagination.*;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/dentists")
public class DentistController {
  private final DentistService service;

  public DentistController(DentistService service) {
    this.service = service;
  }

  @GetMapping
  @PreAuthorize("hasAuthority('DENTISTS_READ')")
  public PageResponse<DentistResponse> list(
      @Valid @ModelAttribute PageQuery query, @RequestParam(required = false) Boolean active) {
    return service.list(query, active);
  }

  @GetMapping("/eligible-users")
  @PreAuthorize("hasAuthority('DENTISTS_WRITE')")
  public PageResponse<EligibleUserResponse> eligibleUsers(@Valid @ModelAttribute PageQuery query) {
    return service.eligibleUsers(query);
  }

  @GetMapping("/{id}/services")
  @PreAuthorize("hasAuthority('DENTISTS_READ')")
  public PageResponse<AssignedServiceResponse> assignedServices(
      @PathVariable UUID id,
      @Valid @ModelAttribute PageQuery query,
      @RequestParam(required = false) Boolean active) {
    return service.assignedServices(id, query, active);
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  @PreAuthorize("hasAuthority('DENTISTS_WRITE')")
  public DentistResponse create(@Valid @RequestBody DentistRequest request) {
    return service.create(request);
  }

  @PutMapping("/{id}")
  @PreAuthorize("hasAuthority('DENTISTS_WRITE')")
  public DentistResponse update(@PathVariable UUID id, @Valid @RequestBody DentistRequest request) {
    return service.update(id, request);
  }
}
