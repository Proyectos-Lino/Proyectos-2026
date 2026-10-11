package com.labcore.platform.results;

import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/work-order-tests/{workOrderTestId}/result")
@PreAuthorize("hasAnyRole('ADMIN','TECHNICAL_MANAGER','ANALYST','VALIDATOR')")
public class ResultController {
  private final ResultService service;

  public ResultController(ResultService service) {
    this.service = service;
  }

  @GetMapping
  public ResultDtos.ResultResponse get(
      @PathVariable UUID workOrderTestId, Authentication authentication) {
    return service.getByWorkOrderTest(workOrderTestId, authentication.getName());
  }

  @GetMapping("/template")
  public ResponseEntity<com.labcore.platform.catalog.TechnicalCatalogDtos.TemplateResponse>
      getTemplate(@PathVariable UUID workOrderTestId, Authentication authentication) {
    return ResponseEntity.of(
        java.util.Optional.ofNullable(
            service.getInitialTemplate(workOrderTestId, authentication.getName())));
  }

  @PostMapping
  @PreAuthorize("hasAnyRole('ADMIN','ANALYST')")
  public ResultDtos.ResultResponse record(
      @PathVariable UUID workOrderTestId,
      @Valid @RequestBody ResultDtos.RecordResultRequest request,
      Authentication authentication) {
    return service.record(workOrderTestId, request, authentication.getName());
  }

  @PostMapping("/matrix-execution/start")
  @PreAuthorize("hasAnyRole('ADMIN','ANALYST')")
  public ResultDtos.ResultResponse startMatrixExecution(
      @PathVariable UUID workOrderTestId,
      @Valid @RequestBody ResultDtos.StartMatrixExecutionRequest request,
      Authentication authentication) {
    return service.startMatrixExecution(workOrderTestId, request, authentication.getName());
  }

  @PostMapping("/matrix-execution/draft")
  @PreAuthorize("hasAnyRole('ADMIN','ANALYST')")
  public ResultDtos.ResultResponse saveMatrixDraft(
      @PathVariable UUID workOrderTestId,
      @Valid @RequestBody ResultDtos.SaveMatrixDraftRequest request,
      Authentication authentication) {
    return service.saveMatrixDraft(workOrderTestId, request, authentication.getName());
  }

  @PostMapping("/calculate-preview")
  @PreAuthorize("hasAnyRole('ADMIN','ANALYST','VALIDATOR')")
  public ResultDtos.CalculationPreviewResponse previewCalculations(
      @PathVariable UUID workOrderTestId,
      @Valid @RequestBody ResultDtos.CalculationPreviewRequest request,
      Authentication authentication) {
    return service.previewCalculations(workOrderTestId, request, authentication.getName());
  }

  @PostMapping("/validate")
  @PreAuthorize("hasAnyRole('ADMIN','VALIDATOR')")
  public ResultDtos.ResultResponse validate(
      @PathVariable UUID workOrderTestId,
      @Valid @RequestBody ResultDtos.ValidateResultRequest request,
      Authentication authentication) {
    return service.validate(workOrderTestId, request, authentication.getName());
  }

  @PostMapping("/reject")
  @PreAuthorize("hasAnyRole('ADMIN','VALIDATOR')")
  public ResultDtos.ResultResponse reject(
      @PathVariable UUID workOrderTestId,
      @Valid @RequestBody ResultDtos.RejectResultRequest request,
      Authentication authentication) {
    return service.reject(workOrderTestId, request, authentication.getName());
  }

  @PostMapping("/correct-and-validate")
  @PreAuthorize("hasAnyRole('ADMIN','VALIDATOR')")
  public ResultDtos.ResultResponse correctAndValidate(
      @PathVariable UUID workOrderTestId,
      @Valid @RequestBody ResultDtos.CorrectAndValidateResultRequest request,
      Authentication authentication) {
    return service.correctAndValidate(workOrderTestId, request, authentication.getName());
  }
}
