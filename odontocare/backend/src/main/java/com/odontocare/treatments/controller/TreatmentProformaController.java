package com.odontocare.treatments.controller;

import com.odontocare.shared.pagination.*;
import com.odontocare.treatments.repository.TreatmentProformaRepository.Document;
import com.odontocare.treatments.service.TreatmentProformaService;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/plans/{planId}/proformas")
@PreAuthorize("hasAuthority('PLANS_READ')")
public class TreatmentProformaController {
  private final TreatmentProformaService service;

  public TreatmentProformaController(TreatmentProformaService service) {
    this.service = service;
  }

  @PostMapping
  public Document emit(@PathVariable UUID planId) {
    return service.emit(planId);
  }

  @GetMapping
  public PageResponse<Document> list(
      @PathVariable UUID planId, @Valid @ModelAttribute PageQuery query) {
    return service.list(planId, query);
  }

  @GetMapping("/{documentId}/content")
  public ResponseEntity<byte[]> download(@PathVariable UUID planId, @PathVariable UUID documentId) {
    return ResponseEntity.ok()
        .contentType(MediaType.APPLICATION_PDF)
        .header(
            HttpHeaders.CONTENT_DISPOSITION,
            "attachment; filename=\"proforma-" + documentId + ".pdf\"")
        .header(HttpHeaders.CACHE_CONTROL, "private, no-store")
        .body(service.download(planId, documentId));
  }
}
