package com.odontocare.dashboard.controller;

import com.odontocare.dashboard.dto.*;
import com.odontocare.dashboard.service.DashboardService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/dashboard")
public class DashboardController {
  private final DashboardService service;

  public DashboardController(DashboardService service) {
    this.service = service;
  }

  @GetMapping
  @PreAuthorize("isAuthenticated()")
  public DashboardContracts.Response get(@Valid @ModelAttribute DashboardQuery query) {
    return service.get(query);
  }
}
