package com.odontocare.finance.controller;

import com.odontocare.documents.service.DocumentPreviewService;
import com.odontocare.finance.dto.PaymentContracts.*;
import com.odontocare.finance.service.FinancialDocumentService;
import com.odontocare.shared.pagination.*;
import jakarta.validation.Valid;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/finance/documents")
public class FinancialDocumentController {
  private final FinancialDocumentService service;

  public FinancialDocumentController(FinancialDocumentService service) {
    this.service = service;
  }

  @GetMapping
  @PreAuthorize("hasAuthority('FINANCES_READ')")
  public PageResponse<DocumentResponse> list(
      @RequestParam(required = false) UUID patientId,
      @RequestParam(required = false) UUID movementId,
      @RequestParam(required = false) Boolean generated,
      @Valid @ModelAttribute PageQuery query) {
    return service.list(patientId, movementId, generated, query);
  }

  @GetMapping("/receipt/{id}")
  @PreAuthorize("hasAuthority('FINANCES_READ')")
  public DocumentResponse receipt(@PathVariable UUID id) {
    return service.receipt(id);
  }

  @PostMapping("/statement")
  @ResponseStatus(HttpStatus.CREATED)
  @PreAuthorize("hasAuthority('FINANCES_READ')")
  public DocumentResponse statement(@Valid @RequestBody StatementRequest r) {
    return service.statement(r);
  }

  @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  @ResponseStatus(HttpStatus.CREATED)
  @PreAuthorize("hasAnyAuthority('PAYMENTS_WRITE','EXPENSES_WRITE')")
  public DocumentResponse upload(
      @Valid @RequestPart("metadata") SupportRequest r, @RequestPart("file") MultipartFile file) {
    return service.upload(r, file);
  }

  @GetMapping("/{id}/content")
  @PreAuthorize("hasAuthority('FINANCES_READ')")
  public ResponseEntity<byte[]> content(@PathVariable UUID id) {
    var d = service.download(id);
    return ResponseEntity.ok()
        .contentType(MediaType.parseMediaType(d.mediaType()))
        .header(
            "Content-Disposition",
            ContentDisposition.attachment()
                .filename(d.fileName(), StandardCharsets.UTF_8)
                .build()
                .toString())
        .header("Cache-Control", "no-store")
        .header("X-Content-Type-Options", "nosniff")
        .header("Content-Security-Policy", "sandbox; default-src 'none'")
        .body(d.bytes());
  }

  @GetMapping("/{id}/preview")
  @PreAuthorize("hasAuthority('FINANCES_READ')")
  public ResponseEntity<byte[]> preview(
      @PathVariable UUID id,
      @RequestParam(defaultValue = "0")
          @jakarta.validation.constraints.Min(0)
          @jakarta.validation.constraints.Max(999)
          int page) {
    var d = service.download(id);
    if (!d.mediaType().equals("application/pdf"))
      throw com.odontocare.shared.web.ApiException.badRequest("Selecciona un PDF.");
    var p = DocumentPreviewService.render(d.bytes(), page);
    return ResponseEntity.ok()
        .contentType(MediaType.IMAGE_PNG)
        .header("Cache-Control", "no-store")
        .header("X-Document-Pages", String.valueOf(p.pages()))
        .body(p.bytes());
  }
}
