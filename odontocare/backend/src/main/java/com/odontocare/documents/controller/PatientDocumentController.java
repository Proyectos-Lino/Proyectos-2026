package com.odontocare.documents.controller;

import com.odontocare.documents.dto.DocumentContracts.*;
import com.odontocare.documents.service.DocumentPreviewService;
import com.odontocare.documents.service.PatientDocumentService;
import com.odontocare.shared.pagination.*;
import jakarta.validation.Valid;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/documents")
public class PatientDocumentController {
  private final PatientDocumentService service;
  private final DocumentPreviewService previews;

  public PatientDocumentController(
      PatientDocumentService service, DocumentPreviewService previews) {
    this.service = service;
    this.previews = previews;
  }

  @GetMapping
  @PreAuthorize("hasAuthority('DOCUMENTS_READ')")
  public PageResponse<DocumentResponse> list(
      @RequestParam UUID patientId,
      @RequestParam(required = false) UUID categoryId,
      @RequestParam(required = false) UUID encounterId,
      @RequestParam(required = false) UUID planId,
      @RequestParam(required = false) Integer tooth,
      @RequestParam(required = false) String mediaType,
      @RequestParam(required = false) LocalDate from,
      @RequestParam(required = false) LocalDate to,
      @Valid @ModelAttribute PageQuery query) {
    return service.list(
        patientId, categoryId, encounterId, planId, tooth, mediaType, from, to, query);
  }

  @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  @ResponseStatus(HttpStatus.CREATED)
  @PreAuthorize("hasAuthority('DOCUMENTS_WRITE')")
  public DocumentResponse upload(
      @Valid @RequestPart("metadata") UploadRequest request,
      @RequestPart("file") MultipartFile file) {
    return service.upload(request, file);
  }

  @GetMapping("/{id}/preview")
  @PreAuthorize("hasAuthority('DOCUMENTS_READ')")
  public ResponseEntity<byte[]> preview(
      @PathVariable UUID id,
      @RequestParam(defaultValue = "0")
          @jakarta.validation.constraints.Min(0)
          @jakarta.validation.constraints.Max(999)
          int page) {
    var preview = previews.preview(id, page);
    return ResponseEntity.ok()
        .contentType(MediaType.IMAGE_PNG)
        .header("Cache-Control", "no-store")
        .header("X-Document-Pages", String.valueOf(preview.pages()))
        .header("X-Content-Type-Options", "nosniff")
        .body(preview.bytes());
  }

  @GetMapping("/{id}/content")
  @PreAuthorize("hasAuthority('DOCUMENTS_READ')")
  public ResponseEntity<byte[]> content(
      @PathVariable UUID id, @RequestParam(defaultValue = "false") boolean download) {
    var document = service.download(id, download);
    return ResponseEntity.ok()
        .contentType(MediaType.parseMediaType(document.mediaType()))
        .contentLength(document.bytes().length)
        .header(
            HttpHeaders.CONTENT_DISPOSITION,
            (download ? ContentDisposition.attachment() : ContentDisposition.inline())
                .filename(document.fileName(), StandardCharsets.UTF_8)
                .build()
                .toString())
        .header(HttpHeaders.CACHE_CONTROL, "no-store")
        .header("Content-Security-Policy", "sandbox; default-src 'none'; frame-ancestors 'self'")
        .header("X-Frame-Options", "SAMEORIGIN")
        .header("X-Content-Type-Options", "nosniff")
        .body(document.bytes());
  }

  @GetMapping("/consents")
  @PreAuthorize("hasAuthority('DOCUMENTS_READ')")
  public PageResponse<ConsentResponse> consents(
      @RequestParam UUID patientId, @Valid @ModelAttribute PageQuery query) {
    return service.consents(patientId, query);
  }

  @PostMapping("/consents")
  @ResponseStatus(HttpStatus.CREATED)
  @PreAuthorize("hasAuthority('DOCUMENTS_WRITE')")
  public ConsentResponse consent(@Valid @RequestBody ConsentRequest request) {
    return service.createConsent(request);
  }
}
