package com.odontocare.reports.controller;

import com.odontocare.reports.dto.*;
import com.odontocare.reports.dto.ReportContracts.*;
import com.odontocare.reports.model.ReportType;
import com.odontocare.reports.service.*;
import jakarta.validation.Valid;
import java.nio.charset.StandardCharsets;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/reports")
public class ReportController {
  private final ReportService service;
  private final ReportExportService exports;

  public ReportController(ReportService service, ReportExportService exports) {
    this.service = service;
    this.exports = exports;
  }

  @GetMapping("/catalog")
  @PreAuthorize("hasAuthority('REPORTS_READ')")
  public Catalog catalog() {
    return service.catalog();
  }

  @GetMapping("/{type}")
  @PreAuthorize("hasAuthority('REPORTS_READ')")
  public Page report(@PathVariable ReportType type, @Valid @ModelAttribute ReportQuery query) {
    return service.page(type, query);
  }

  @GetMapping("/{type}/export")
  @PreAuthorize("hasAuthority('REPORTS_READ') and hasAuthority('REPORTS_EXPORT')")
  public ResponseEntity<byte[]> export(
      @PathVariable ReportType type,
      @Valid @ModelAttribute ReportQuery query,
      @RequestParam ReportExportService.Format format) {
    var file = exports.export(type, query, format);
    return ResponseEntity.ok()
        .contentType(MediaType.parseMediaType(file.mediaType()))
        .header(
            "Content-Disposition",
            ContentDisposition.attachment()
                .filename(file.name(), StandardCharsets.UTF_8)
                .build()
                .toString())
        .header("Cache-Control", "no-store")
        .header("X-Content-Type-Options", "nosniff")
        .header("Content-Security-Policy", "sandbox; default-src 'none'")
        .body(file.bytes());
  }
}
