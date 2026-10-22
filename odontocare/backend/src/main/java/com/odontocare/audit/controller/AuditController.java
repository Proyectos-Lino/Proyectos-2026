package com.odontocare.audit.controller;

import com.odontocare.audit.dto.AuditResponse;
import com.odontocare.audit.service.AuditQueryService;
import com.odontocare.shared.pagination.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/audit-events")
public class AuditController {
  private final AuditQueryService service;

  public AuditController(AuditQueryService service) {
    this.service = service;
  }

  @GetMapping
  @PreAuthorize("hasAuthority('AUDIT_READ')")
  public PageResponse<AuditResponse> list(
      @Valid @ModelAttribute PageQuery query,
      @RequestParam(required = false) @Size(max = 40) String entityType,
      @RequestParam(required = false) @Size(max = 40) String action,
      @RequestParam(required = false) LocalDate fromDate,
      @RequestParam(required = false) LocalDate toDate) {
    return service.list(query, entityType, action, fromDate, toDate);
  }
}
