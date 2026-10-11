package com.labcore.platform.workorders;

import com.labcore.platform.common.export.CsvFile;
import com.labcore.platform.common.i18n.SystemLabel;
import com.labcore.platform.common.web.PageResponse;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
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
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/work-orders")
public class WorkOrderController {
  private final WorkOrderService service;

  public WorkOrderController(WorkOrderService service) {
    this.service = service;
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  @PreAuthorize("hasAnyRole('ADMIN','TECHNICAL_MANAGER')")
  public WorkOrderDtos.WorkOrderDetail create(
      @Valid @RequestBody WorkOrderDtos.CreateWorkOrderRequest request,
      Authentication authentication) {
    return service.create(request, authentication.getName());
  }

  @GetMapping
  @PreAuthorize("hasAnyRole('ADMIN','TECHNICAL_MANAGER','ANALYST','VALIDATOR')")
  public PageResponse<WorkOrderDtos.WorkOrderSummary> list(
      @RequestParam(required = false) String search,
      @RequestParam(required = false) WorkOrderStatus status,
      @RequestParam(required = false) UUID laboratoryAreaId,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
          java.time.Instant laboratoryDueFrom,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
          java.time.Instant laboratoryDueTo,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
          java.time.Instant createdFrom,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
          java.time.Instant createdTo,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size,
      Authentication authentication) {
    return PageResponse.from(
        service.getPage(
            search,
            status,
            laboratoryAreaId,
            laboratoryDueFrom,
            laboratoryDueTo,
            createdFrom,
            createdTo,
            PageRequest.of(
                Math.max(page, 0),
                Math.min(Math.max(size, 1), 100),
                Sort.by(Sort.Direction.DESC, "createdAt")),
            authentication.getName()));
  }

  @GetMapping("/export")
  @PreAuthorize("hasAnyRole('ADMIN','TECHNICAL_MANAGER','ANALYST','VALIDATOR')")
  public ResponseEntity<byte[]> export(
      @RequestParam(required = false) String search,
      @RequestParam(required = false) WorkOrderStatus status,
      @RequestParam(required = false) UUID laboratoryAreaId,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
          java.time.Instant laboratoryDueFrom,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
          java.time.Instant laboratoryDueTo,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
          java.time.Instant createdFrom,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
          java.time.Instant createdTo,
      Authentication authentication) {
    CsvFile csv =
        new CsvFile()
            .row(
                "Orden de trabajo",
                "Orden de servicio",
                "Área",
                "Responsable",
                "Fecha límite",
                "Estado",
                "Muestras");
    service
        .getPage(
            search,
            status,
            laboratoryAreaId,
            laboratoryDueFrom,
            laboratoryDueTo,
            createdFrom,
            createdTo,
            PageRequest.of(0, 10_000, Sort.by(Sort.Direction.DESC, "createdAt")),
            authentication.getName())
        .forEach(
            order ->
                csv.row(
                    order.code(),
                    order.serviceOrderCode(),
                    order.laboratoryAreaName(),
                    order.responsibleName(),
                    order.laboratoryDueAt(),
                    SystemLabel.forValue(order.status()),
                    order.sampleCount()));
    return ResponseEntity.ok()
        .contentType(new MediaType("text", "csv", java.nio.charset.StandardCharsets.UTF_8))
        .header(
            HttpHeaders.CONTENT_DISPOSITION,
            ContentDisposition.attachment().filename("ordenes-trabajo.csv").build().toString())
        .body(csv.bytes());
  }

  @GetMapping("/eligible-service-orders")
  @PreAuthorize("hasAnyRole('ADMIN','TECHNICAL_MANAGER')")
  public java.util.List<WorkOrderDtos.EligibleServiceOrder> eligibleServiceOrders(
      Authentication authentication) {
    return service.getEligibleServiceOrders(authentication.getName());
  }

  @GetMapping("/eligible-service-orders/{serviceOrderId}")
  @PreAuthorize("hasAnyRole('ADMIN','TECHNICAL_MANAGER')")
  public WorkOrderDtos.EligibleServiceOrderDetail eligibleServiceOrder(
      @PathVariable UUID serviceOrderId, Authentication authentication) {
    return service.getEligibleServiceOrder(serviceOrderId, authentication.getName());
  }

  @GetMapping("/{id}")
  @PreAuthorize("hasAnyRole('ADMIN','TECHNICAL_MANAGER','ANALYST','VALIDATOR')")
  public WorkOrderDtos.WorkOrderDetail detail(
      @PathVariable UUID id, Authentication authentication) {
    return service.getDetail(id, authentication.getName());
  }

  @GetMapping("/{id}/history")
  @PreAuthorize("hasAnyRole('ADMIN','TECHNICAL_MANAGER','ANALYST','VALIDATOR')")
  public PageResponse<WorkOrderDtos.WorkOrderHistoryEntry> history(
      @PathVariable UUID id, Pageable pageable, Authentication authentication) {
    return PageResponse.from(
        service.getHistory(
            id,
            PageRequest.of(
                Math.max(pageable.getPageNumber(), 0),
                Math.min(Math.max(pageable.getPageSize(), 1), 100),
                pageable.getSort()),
            authentication.getName()));
  }

  @GetMapping("/{id}/document")
  @PreAuthorize("hasAnyRole('ADMIN','TECHNICAL_MANAGER','ANALYST','VALIDATOR')")
  public WorkOrderDtos.WorkOrderDocumentResponse document(
      @PathVariable UUID id, Authentication authentication) {
    return service.getDocument(id, authentication.getName());
  }

  @PostMapping("/{id}/cancel")
  @PreAuthorize("hasAnyRole('ADMIN','TECHNICAL_MANAGER')")
  public ResponseEntity<Void> cancel(
      @PathVariable UUID id,
      @Valid @RequestBody WorkOrderDtos.CancelWorkOrderRequest request,
      Authentication authentication) {
    service.cancel(id, request, authentication.getName());
    return ResponseEntity.noContent().build();
  }

  @PostMapping("/{id}/close")
  @PreAuthorize("hasAnyRole('ADMIN','TECHNICAL_MANAGER')")
  public ResponseEntity<Void> close(@PathVariable UUID id, Authentication authentication) {
    service.close(id, authentication.getName());
    return ResponseEntity.noContent().build();
  }
}
