package com.labcore.platform.orders;

import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/service-orders/{orderId}")
public class ServiceOrderCorrectionController {

  private final ServiceOrderCorrectionWorkflowService workflowService;

  public ServiceOrderCorrectionController(ServiceOrderCorrectionWorkflowService workflowService) {
    this.workflowService = workflowService;
  }

  @PostMapping("/correction-impact")
  @PreAuthorize("hasAnyRole('ADMIN', 'COMMERCIAL')")
  public ServiceOrderDtos.CorrectionImpactResponse correctionImpact(
      @PathVariable UUID orderId,
      @Valid @RequestBody ServiceOrderDtos.UpdateOrderSamplesRequest request,
      Authentication authentication) {
    return workflowService.assessImpact(orderId, request, authentication.getName());
  }

  @PostMapping("/return-to-commercial")
  @PreAuthorize("hasAnyRole('ADMIN', 'TECHNICAL_MANAGER')")
  public ServiceOrderDtos.ServiceOrderDetail returnToCommercial(
      @PathVariable UUID orderId,
      @Valid @RequestBody ServiceOrderDtos.RequestCommercialCorrectionRequest request,
      Authentication authentication) {
    return workflowService.requestCorrection(orderId, request, authentication.getName());
  }

  @PostMapping("/submit-correction")
  @PreAuthorize("hasAnyRole('ADMIN', 'COMMERCIAL')")
  public ServiceOrderDtos.ServiceOrderDetail submitCorrection(
      @PathVariable UUID orderId,
      @Valid @RequestBody ServiceOrderDtos.SubmitCommercialCorrectionRequest request,
      Authentication authentication) {
    return workflowService.submitCorrection(orderId, request, authentication.getName());
  }

  @PostMapping("/approve-correction")
  @PreAuthorize("hasAnyRole('ADMIN', 'TECHNICAL_MANAGER')")
  public ServiceOrderDtos.ServiceOrderDetail approveCorrection(
      @PathVariable UUID orderId, Authentication authentication) {
    return workflowService.approveCorrection(orderId, authentication.getName());
  }
}
