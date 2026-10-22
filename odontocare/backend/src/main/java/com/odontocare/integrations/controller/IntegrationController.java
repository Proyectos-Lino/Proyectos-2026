package com.odontocare.integrations.controller;

import com.odontocare.integrations.dto.IntegrationContracts.*;
import com.odontocare.integrations.service.*;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/settings/integrations")
@PreAuthorize("hasAuthority('INTEGRATIONS_MANAGE')")
public class IntegrationController {
  private final IntegrationConfigurationService service;
  private final IntegrationCheckService checks;

  public IntegrationController(
      IntegrationConfigurationService service, IntegrationCheckService checks) {
    this.service = service;
    this.checks = checks;
  }

  @GetMapping
  public View get() {
    return service.get();
  }

  @PutMapping
  public View save(@Valid @RequestBody Save request) {
    return service.save(request);
  }

  @PostMapping("/check/{provider}")
  public ProbeResult check(@PathVariable String provider, @Valid @RequestBody Check check) {
    return checks.check(provider, check.version());
  }
}
