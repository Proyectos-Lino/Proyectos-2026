package com.labcore.platform.reports;

import com.labcore.platform.common.web.PageResponse;
import jakarta.validation.Valid;
import java.time.Instant;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/reports")
@PreAuthorize("hasAnyRole('ADMIN','COMMERCIAL','TECHNICAL_MANAGER','VALIDATOR')")
public class LaboratoryReportController {
  private final LaboratoryReportService service;

  public LaboratoryReportController(LaboratoryReportService service) {
    this.service = service;
  }

  @GetMapping
  public PageResponse<LaboratoryReportDtos.Summary> list(
      @RequestParam(required = false) String search,
      @RequestParam(required = false) LaboratoryReportStatus status,
      @RequestParam(required = false) UUID serviceOrderId,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
          Instant issuedFrom,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
          Instant issuedTo,
      @RequestParam(required = false) UUID laboratoryAreaId,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size,
      Authentication authentication) {
    return PageResponse.from(
        service.list(
            search,
            status,
            serviceOrderId,
            issuedFrom,
            issuedTo,
            laboratoryAreaId,
            PageRequest.of(
                Math.max(0, page),
                Math.min(Math.max(1, size), 100),
                Sort.by(Sort.Direction.DESC, "createdAt")),
            authentication.getName()));
  }

  @PostMapping("/service-orders/{serviceOrderId}")
  @PreAuthorize("hasAnyRole('ADMIN','TECHNICAL_MANAGER','VALIDATOR')")
  public LaboratoryReportDtos.Detail createDraft(
      @PathVariable UUID serviceOrderId, Authentication authentication) {
    return service.createDraft(serviceOrderId, authentication.getName());
  }

  @GetMapping("/preparation-candidates")
  @PreAuthorize("hasAnyRole('ADMIN','TECHNICAL_MANAGER','VALIDATOR')")
  public java.util.List<LaboratoryReportDtos.PreparationCandidate> preparationCandidates(
      Authentication authentication) {
    return service.preparationCandidates(authentication.getName());
  }

  @GetMapping("/export")
  @PreAuthorize("hasAnyRole('ADMIN','COMMERCIAL')")
  public ResponseEntity<byte[]> export(
      @RequestParam(required = false) String search,
      @RequestParam(required = false) LaboratoryReportStatus status,
      @RequestParam(required = false) UUID serviceOrderId,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
          Instant issuedFrom,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
          Instant issuedTo,
      @RequestParam(required = false) UUID laboratoryAreaId,
      Authentication authentication) {
    return ResponseEntity.ok()
        .contentType(new MediaType("text", "csv", java.nio.charset.StandardCharsets.UTF_8))
        .header(
            HttpHeaders.CONTENT_DISPOSITION,
            ContentDisposition.attachment().filename("informes-resultados.csv").build().toString())
        .body(
            service.export(
                search,
                status,
                serviceOrderId,
                issuedFrom,
                issuedTo,
                laboratoryAreaId,
                authentication.getName()));
  }

  @GetMapping("/{reportId}")
  public LaboratoryReportDtos.Detail get(
      @PathVariable UUID reportId, Authentication authentication) {
    return service.get(reportId, authentication.getName());
  }

  @GetMapping("/{reportId}/pdf")
  public ResponseEntity<byte[]> pdf(@PathVariable UUID reportId, Authentication authentication) {
    LaboratoryReportDtos.PdfFile pdf = service.download(reportId, authentication.getName());
    return ResponseEntity.ok()
        .contentType(MediaType.APPLICATION_PDF)
        .header(
            HttpHeaders.CONTENT_DISPOSITION,
            ContentDisposition.inline().filename(pdf.reportCode() + ".pdf").build().toString())
        .body(pdf.content());
  }

  @GetMapping("/{reportId}/history")
  public PageResponse<LaboratoryReportDtos.HistoryEntry> history(
      @PathVariable UUID reportId,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size,
      Authentication authentication) {
    return PageResponse.from(
        service.history(
            reportId,
            PageRequest.of(
                Math.max(0, page),
                Math.min(Math.max(1, size), 100),
                Sort.by(Sort.Direction.DESC, "occurredAt")),
            authentication.getName()));
  }

  @PostMapping("/{reportId}/issue")
  @PreAuthorize("hasAnyRole('ADMIN','TECHNICAL_MANAGER')")
  public LaboratoryReportDtos.Detail issue(
      @PathVariable UUID reportId, Authentication authentication) {
    return service.issue(reportId, authentication.getName());
  }

  @PostMapping("/{reportId}/cancel")
  @PreAuthorize("hasAnyRole('ADMIN','TECHNICAL_MANAGER')")
  public LaboratoryReportDtos.Detail cancel(
      @PathVariable UUID reportId,
      @Valid @RequestBody LaboratoryReportDtos.CancelRequest request,
      Authentication authentication) {
    return service.cancel(reportId, request, authentication.getName());
  }

  @PostMapping("/{reportId}/reissue")
  @PreAuthorize("hasAnyRole('ADMIN','TECHNICAL_MANAGER')")
  public LaboratoryReportDtos.Detail reissue(
      @PathVariable UUID reportId, Authentication authentication) {
    return service.reissue(reportId, authentication.getName());
  }
}
