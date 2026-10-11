package com.labcore.platform.organization;

import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/organization-profile")
public class OrganizationProfileController {
  private final OrganizationProfileService service;

  public OrganizationProfileController(OrganizationProfileService service) {
    this.service = service;
  }

  @GetMapping
  @PreAuthorize("hasRole('ADMIN')")
  public OrganizationDtos.OrganizationResponse get() {
    return service.get();
  }

  @GetMapping("/public")
  public OrganizationDtos.BrandingResponse branding() {
    return service.branding();
  }

  @PutMapping
  @PreAuthorize("hasRole('ADMIN')")
  public OrganizationDtos.OrganizationResponse update(
      @Valid @RequestBody OrganizationDtos.OrganizationRequest request,
      Authentication authentication) {
    return service.update(request, authentication.getName());
  }
}
