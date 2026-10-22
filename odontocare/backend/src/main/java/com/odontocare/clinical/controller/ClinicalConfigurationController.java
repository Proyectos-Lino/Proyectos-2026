package com.odontocare.clinical.controller;

import com.odontocare.clinical.dto.ClinicalContracts.*;
import com.odontocare.clinical.service.ClinicalConfigurationService;
import com.odontocare.documents.dto.DocumentContracts.*;
import com.odontocare.shared.pagination.*;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
public class ClinicalConfigurationController {
  private final ClinicalConfigurationService service;

  public ClinicalConfigurationController(ClinicalConfigurationService service) {
    this.service = service;
  }

  @GetMapping("/clinical/templates")
  @PreAuthorize("hasAuthority('CLINICAL_CONFIG_READ')")
  public PageResponse<TemplateResponse> templates(
      @Valid @ModelAttribute PageQuery query,
      @RequestParam(required = false) Boolean active,
      @RequestParam(required = false) String kind) {
    return service.templates(query, active, kind);
  }

  @GetMapping("/clinical/templates/{id}")
  @PreAuthorize("hasAuthority('CLINICAL_CONFIG_READ')")
  public TemplateResponse template(@PathVariable UUID id) {
    return service.template(id);
  }

  @PostMapping("/clinical/templates")
  @ResponseStatus(org.springframework.http.HttpStatus.CREATED)
  @PreAuthorize("hasAuthority('CLINICAL_CONFIG_WRITE')")
  public TemplateResponse createTemplate(@Valid @RequestBody TemplateRequest request) {
    return service.saveTemplate(null, request);
  }

  @PutMapping("/clinical/templates/{id}")
  @PreAuthorize("hasAuthority('CLINICAL_CONFIG_WRITE')")
  public TemplateResponse updateTemplate(
      @PathVariable UUID id, @Valid @RequestBody TemplateRequest request) {
    return service.saveTemplate(id, request);
  }

  @GetMapping("/documents/categories")
  @PreAuthorize("hasAnyAuthority('CLINICAL_CONFIG_READ','DOCUMENTS_READ')")
  public PageResponse<CategoryResponse> categories(
      @Valid @ModelAttribute PageQuery query, @RequestParam(required = false) Boolean active) {
    return service.categories(query, active);
  }

  @PostMapping("/documents/categories")
  @ResponseStatus(org.springframework.http.HttpStatus.CREATED)
  @PreAuthorize("hasAuthority('CLINICAL_CONFIG_WRITE')")
  public CategoryResponse createCategory(@Valid @RequestBody CategoryRequest request) {
    return service.saveCategory(null, request);
  }

  @PutMapping("/documents/categories/{id}")
  @PreAuthorize("hasAuthority('CLINICAL_CONFIG_WRITE')")
  public CategoryResponse updateCategory(
      @PathVariable UUID id, @Valid @RequestBody CategoryRequest request) {
    return service.saveCategory(id, request);
  }

  @GetMapping("/documents/policy")
  @PreAuthorize("hasAnyAuthority('DOCUMENTS_READ','CLINICAL_CONFIG_READ')")
  public PolicyResponse policy() {
    return service.policy();
  }

  @PutMapping("/documents/policy")
  @PreAuthorize("hasAuthority('CLINICAL_CONFIG_WRITE')")
  public PolicyResponse savePolicy(@Valid @RequestBody PolicyRequest request) {
    return service.savePolicy(request);
  }
}
