package com.odontocare.installation.controller;

import com.odontocare.installation.dto.InstallationResponse;
import com.odontocare.installation.service.InstallationService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/system/installation")
public class InstallationController {
  private final InstallationService service;

  public InstallationController(InstallationService service) {
    this.service = service;
  }

  @GetMapping
  public InstallationResponse getInstallation() {
    return service.getInstallation();
  }
}
