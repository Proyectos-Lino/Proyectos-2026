package com.labcore.platform.workflow;

import com.labcore.platform.results.ResultDtos;
import com.labcore.platform.workorders.WorkOrderDtos;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
@PreAuthorize("hasAnyRole('ADMIN','TECHNICAL_MANAGER')")
public class OperationCorrectionController {
  private final TechnicalCorrectionService corrections;
  private final com.labcore.platform.workorders.WorkOrderService workOrders;

  public OperationCorrectionController(
      TechnicalCorrectionService corrections,
      com.labcore.platform.workorders.WorkOrderService workOrders) {
    this.corrections = corrections;
    this.workOrders = workOrders;
  }

  @PostMapping("/work-orders/{id}/return-to-previous")
  public WorkOrderDtos.WorkOrderDetail returnWorkOrder(
      @PathVariable UUID id,
      @Valid @RequestBody OperationCorrectionDtos.ReturnRequest request,
      Authentication authentication) {
    return corrections.returnWorkOrder(id, request.reason().trim(), authentication.getName());
  }

  @PostMapping("/work-order-tests/{id}/result/return-to-previous")
  public ResultDtos.ResultResponse returnResult(
      @PathVariable UUID id,
      @Valid @RequestBody OperationCorrectionDtos.ReturnRequest request,
      Authentication authentication) {
    return corrections.returnResult(id, request.reason().trim(), authentication.getName());
  }

  @PostMapping("/work-orders/{id}/correct-assignments")
  public WorkOrderDtos.WorkOrderDetail correctAssignments(
      @PathVariable UUID id,
      @Valid @RequestBody OperationCorrectionDtos.CorrectAssignmentRequest request,
      Authentication authentication) {
    return workOrders.correctAssignments(id, request, authentication.getName());
  }

  @PostMapping("/reports/{id}/return-to-previous")
  public org.springframework.http.ResponseEntity<Void> returnReport(
      @PathVariable UUID id,
      @Valid @RequestBody OperationCorrectionDtos.ReturnRequest request,
      Authentication authentication) {
    corrections.returnReport(id, request.reason().trim(), authentication.getName());
    return org.springframework.http.ResponseEntity.noContent().build();
  }
}
