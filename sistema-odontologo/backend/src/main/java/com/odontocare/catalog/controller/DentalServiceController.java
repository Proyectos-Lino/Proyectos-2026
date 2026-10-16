package com.odontocare.catalog.controller;

import com.odontocare.catalog.dto.*;
import com.odontocare.catalog.service.DentalServiceService;
import com.odontocare.shared.pagination.*;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/services")
public class DentalServiceController {
  private final DentalServiceService service;

  public DentalServiceController(DentalServiceService service) {
    this.service = service;
  }

  @GetMapping
  @PreAuthorize("hasAuthority('SERVICES_READ')")
  public PageResponse<ServiceResponse> list(
      @Valid @ModelAttribute PageQuery query,
      @RequestParam(required = false) Boolean active,
      @RequestParam(required = false) UUID categoryId,
      @RequestParam(required = false) Boolean bookable,
      @RequestParam(required = false) UUID dentistId) {
    return service.list(query, active, categoryId, bookable, dentistId);
  }

  @GetMapping("/{id}")
  @PreAuthorize("hasAuthority('SERVICES_READ')")
  public ServiceResponse get(@PathVariable UUID id) {
    return service.get(id);
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  @PreAuthorize("hasAuthority('SERVICES_WRITE')")
  public ServiceResponse create(@Valid @RequestBody ServiceRequest request) {
    return service.create(request);
  }

  @PutMapping("/{id}")
  @PreAuthorize("hasAuthority('SERVICES_WRITE')")
  public ServiceResponse update(@PathVariable UUID id, @Valid @RequestBody ServiceRequest request) {
    return service.update(id, request);
  }
}
