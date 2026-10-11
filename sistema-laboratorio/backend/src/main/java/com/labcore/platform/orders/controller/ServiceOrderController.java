package com.labcore.platform.orders;

import com.labcore.platform.common.export.CsvFile;
import com.labcore.platform.common.i18n.SystemLabel;
import com.labcore.platform.common.web.PageResponse;
import jakarta.validation.Valid;
import java.net.URI;
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
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/service-orders")
public class ServiceOrderController {

  private final ServiceOrderService serviceOrderService;
  private final ServiceOrderDocumentService documentService;

  public ServiceOrderController(
      ServiceOrderService serviceOrderService, ServiceOrderDocumentService documentService) {
    this.serviceOrderService = serviceOrderService;
    this.documentService = documentService;
  }

  @PostMapping
  @PreAuthorize("hasAnyRole('ADMIN', 'COMMERCIAL')")
  public ResponseEntity<ServiceOrderDtos.ServiceOrderDetail> create(
      @Valid @RequestBody ServiceOrderDtos.CreateServiceOrderRequest request,
      Authentication authentication) {
    ServiceOrderDtos.ServiceOrderDetail response =
        serviceOrderService.create(request, authentication.getName());
    return ResponseEntity.created(URI.create("/api/v1/service-orders/" + response.id()))
        .body(response);
  }

  @GetMapping
  @PreAuthorize("hasAnyRole('ADMIN', 'COMMERCIAL')")
  public PageResponse<ServiceOrderDtos.ServiceOrderSummary> list(
      @RequestParam(required = false) String search,
      @RequestParam(required = false) ServiceOrderStatus status,
      @RequestParam(required = false) UUID clientId,
      @RequestParam(required = false) UUID laboratoryAreaId,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
          Instant plannedDeliveryFrom,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
          Instant plannedDeliveryTo,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
          Instant createdFrom,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
          Instant createdTo,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    return PageResponse.from(
        serviceOrderService.getPage(
            search,
            status,
            clientId,
            laboratoryAreaId,
            plannedDeliveryFrom,
            plannedDeliveryTo,
            createdFrom,
            createdTo,
            PageRequest.of(
                Math.max(0, page),
                Math.min(Math.max(1, size), 100),
                Sort.by(Sort.Direction.DESC, "createdAt"))));
  }

  @GetMapping("/export")
  @PreAuthorize("hasAnyRole('ADMIN', 'COMMERCIAL')")
  public ResponseEntity<byte[]> export(
      @RequestParam(required = false) String search,
      @RequestParam(required = false) ServiceOrderStatus status,
      @RequestParam(required = false) UUID clientId,
      @RequestParam(required = false) UUID laboratoryAreaId,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
          Instant plannedDeliveryFrom,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
          Instant plannedDeliveryTo,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
          Instant createdFrom,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
          Instant createdTo) {
    CsvFile csv =
        new CsvFile()
            .row(
                "Orden",
                "Cliente",
                "Área",
                "Estado",
                "Entrega programada",
                "Proforma",
                "Subcontratada",
                "Muestras");
    serviceOrderService
        .getPage(
            search,
            status,
            clientId,
            laboratoryAreaId,
            plannedDeliveryFrom,
            plannedDeliveryTo,
            createdFrom,
            createdTo,
            PageRequest.of(0, 10_000, Sort.by(Sort.Direction.DESC, "createdAt")))
        .forEach(
            order ->
                csv.row(
                    order.code(),
                    order.clientName(),
                    order.laboratoryAreaName(),
                    SystemLabel.forValue(order.status()),
                    order.plannedDeliveryAt(),
                    order.proformaNumber(),
                    order.outsourced() ? "Sí" : "No",
                    order.sampleCount()));
    return ResponseEntity.ok()
        .contentType(new MediaType("text", "csv", java.nio.charset.StandardCharsets.UTF_8))
        .header(
            HttpHeaders.CONTENT_DISPOSITION,
            ContentDisposition.attachment().filename("ordenes-servicio.csv").build().toString())
        .body(csv.bytes());
  }

  @GetMapping("/{orderId}")
  @PreAuthorize("hasAnyRole('ADMIN', 'COMMERCIAL')")
  public ServiceOrderDtos.ServiceOrderDetail getDetail(@PathVariable UUID orderId) {
    return serviceOrderService.getDetail(orderId);
  }

  @GetMapping("/{orderId}/document")
  @PreAuthorize("hasAnyRole('ADMIN', 'COMMERCIAL')")
  public ServiceOrderDtos.ServiceOrderDocumentResponse getDocument(
      @PathVariable UUID orderId, Authentication authentication) {
    return documentService.getDocument(orderId, authentication.getName());
  }

  @GetMapping("/{orderId}/history")
  @PreAuthorize("hasAnyRole('ADMIN', 'COMMERCIAL')")
  public PageResponse<ServiceOrderDtos.ServiceOrderHistoryEntry> history(
      @PathVariable UUID orderId,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    return PageResponse.from(
        serviceOrderService.getHistory(
            orderId,
            PageRequest.of(
                Math.max(0, page),
                Math.min(Math.max(1, size), 100),
                Sort.by(Sort.Direction.DESC, "occurredAt"))));
  }

  @PutMapping("/{orderId}/commercial-details")
  @PreAuthorize("hasAnyRole('ADMIN', 'COMMERCIAL')")
  public ServiceOrderDtos.ServiceOrderDetail updateCommercialDetails(
      @PathVariable UUID orderId,
      @Valid @RequestBody ServiceOrderDtos.UpdateCommercialDetailsRequest request,
      Authentication authentication) {
    return serviceOrderService.updateCommercialDetails(orderId, request, authentication.getName());
  }

  @PutMapping("/{orderId}/samples")
  @PreAuthorize("hasAnyRole('ADMIN', 'COMMERCIAL')")
  public ServiceOrderDtos.ServiceOrderDetail updateSamples(
      @PathVariable UUID orderId,
      @Valid @RequestBody ServiceOrderDtos.UpdateOrderSamplesRequest request,
      Authentication authentication) {
    return serviceOrderService.updateSamples(orderId, request, authentication.getName());
  }

  @PostMapping("/{orderId}/confirm")
  @PreAuthorize("hasAnyRole('ADMIN', 'COMMERCIAL')")
  public ResponseEntity<Void> confirmOrder(
      @PathVariable UUID orderId, Authentication authentication) {
    serviceOrderService.confirmOrder(orderId, authentication.getName());
    return ResponseEntity.noContent().build();
  }

  /** Compatibility alias for clients created before the confirmation workflow was formalized. */
  @Deprecated
  @PostMapping("/{orderId}/start")
  @PreAuthorize("hasAnyRole('ADMIN', 'COMMERCIAL')")
  public ResponseEntity<Void> startOrderCompatibilityAlias(
      @PathVariable UUID orderId, Authentication authentication) {
    serviceOrderService.confirmOrder(orderId, authentication.getName());
    return ResponseEntity.noContent().build();
  }

  @PostMapping("/{orderId}/finalize")
  @PreAuthorize("hasAnyRole('ADMIN', 'COMMERCIAL')")
  public ResponseEntity<Void> finalizeOrder(
      @PathVariable UUID orderId, Authentication authentication) {
    serviceOrderService.finalizeOrder(orderId, authentication.getName());
    return ResponseEntity.noContent().build();
  }

  @PostMapping("/{orderId}/cancel")
  @PreAuthorize("hasAnyRole('ADMIN', 'COMMERCIAL')")
  public ResponseEntity<Void> cancel(
      @PathVariable UUID orderId,
      @Valid @RequestBody ServiceOrderDtos.CancelServiceOrderRequest request,
      Authentication authentication) {
    serviceOrderService.cancelOrder(orderId, request, authentication.getName());
    return ResponseEntity.noContent().build();
  }
}
