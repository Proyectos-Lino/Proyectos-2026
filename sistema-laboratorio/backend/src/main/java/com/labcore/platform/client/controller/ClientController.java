package com.labcore.platform.client;

import com.labcore.platform.common.export.CsvFile;
import com.labcore.platform.common.i18n.SystemLabel;
import com.labcore.platform.common.web.PageResponse;
import com.labcore.platform.orders.ServiceOrderDtos;
import com.labcore.platform.orders.ServiceOrderService;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/clients")
public class ClientController {

  private final ClientService clientService;
  private final ServiceOrderService serviceOrderService;

  public ClientController(ClientService clientService, ServiceOrderService serviceOrderService) {
    this.clientService = clientService;
    this.serviceOrderService = serviceOrderService;
  }

  @GetMapping
  @PreAuthorize("hasAnyRole('ADMIN', 'COMMERCIAL')")
  public PageResponse<ClientDtos.ClientResponse> search(
      @RequestParam(required = false) String search,
      @RequestParam(required = false) DocumentType documentType,
      @RequestParam(required = false) ParticipantType participantType,
      @RequestParam(required = false) Boolean active,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    Pageable pageable =
        PageRequest.of(
            Math.max(0, page),
            Math.min(Math.max(1, size), 100),
            Sort.by(Sort.Direction.ASC, "legalName"));
    return PageResponse.from(
        clientService.search(search, documentType, participantType, active, pageable));
  }

  @GetMapping("/{clientId}")
  @PreAuthorize("hasAnyRole('ADMIN', 'COMMERCIAL')")
  public ClientDtos.ClientResponse get(@PathVariable UUID clientId) {
    return ClientDtos.ClientResponse.from(clientService.getRequired(clientId));
  }

  @GetMapping("/export")
  @PreAuthorize("hasAnyRole('ADMIN', 'COMMERCIAL')")
  public ResponseEntity<byte[]> export(
      @RequestParam(required = false) String search,
      @RequestParam(required = false) DocumentType documentType,
      @RequestParam(required = false) ParticipantType participantType,
      @RequestParam(required = false) Boolean active) {
    CsvFile csv =
        new CsvFile()
            .row(
                "Tipo de documento",
                "Documento",
                "Clasificación",
                "Razón social",
                "Nombre de informe",
                "Correo",
                "Contacto",
                "Teléfono",
                "Estado");
    clientService
        .search(search, documentType, participantType, active, PageRequest.of(0, 10_000))
        .forEach(
            client ->
                csv.row(
                    SystemLabel.forValue(client.documentType()),
                    client.documentNumber(),
                    participantLabel(client.participantType()),
                    client.legalName(),
                    client.reportName(),
                    client.email(),
                    client.contactName(),
                    client.phone(),
                    client.active() ? "Activo" : "Inactivo"));
    return csvResponse(csv.bytes(), "clientes-productores.csv");
  }

  @GetMapping("/{clientId}/orders")
  @PreAuthorize("hasAnyRole('ADMIN', 'COMMERCIAL')")
  public PageResponse<ServiceOrderDtos.ServiceOrderSummary> history(
      @PathVariable UUID clientId,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    clientService.getRequired(clientId);
    return PageResponse.from(
        serviceOrderService.getPageByClient(
            clientId,
            PageRequest.of(
                Math.max(0, page),
                Math.min(Math.max(1, size), 100),
                Sort.by(Sort.Direction.DESC, "createdAt"))));
  }

  @GetMapping("/{clientId}/producer-samples")
  @PreAuthorize("hasAnyRole('ADMIN', 'COMMERCIAL')")
  public PageResponse<ServiceOrderDtos.ProducerSampleHistory> producerHistory(
      @PathVariable UUID clientId,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    clientService.getRequired(clientId);
    return PageResponse.from(
        serviceOrderService.getPageByProducer(
            clientId,
            PageRequest.of(
                Math.max(0, page),
                Math.min(Math.max(1, size), 100),
                Sort.by(Sort.Direction.DESC, "createdAt"))));
  }

  @PostMapping
  @PreAuthorize("hasAnyRole('ADMIN', 'COMMERCIAL')")
  public ResponseEntity<ClientDtos.ClientResponse> create(
      @Valid @RequestBody ClientDtos.ClientRequest request) {
    ClientDtos.ClientResponse response = clientService.create(request);
    return ResponseEntity.created(URI.create("/api/v1/clients/" + response.id())).body(response);
  }

  @PutMapping("/{clientId}")
  @PreAuthorize("hasAnyRole('ADMIN', 'COMMERCIAL')")
  public ClientDtos.ClientResponse update(
      @PathVariable UUID clientId, @Valid @RequestBody ClientDtos.ClientRequest request) {
    return clientService.update(clientId, request);
  }

  @PatchMapping("/{clientId}/status")
  @PreAuthorize("hasAnyRole('ADMIN', 'COMMERCIAL')")
  public ClientDtos.ClientResponse updateStatus(
      @PathVariable UUID clientId, @Valid @RequestBody ClientDtos.StatusRequest request) {
    return clientService.updateStatus(clientId, request);
  }

  private ResponseEntity<byte[]> csvResponse(byte[] content, String filename) {
    return ResponseEntity.ok()
        .contentType(new MediaType("text", "csv", java.nio.charset.StandardCharsets.UTF_8))
        .header(
            HttpHeaders.CONTENT_DISPOSITION,
            ContentDisposition.attachment().filename(filename).build().toString())
        .body(content);
  }

  private String participantLabel(ParticipantType participantType) {
    return switch (participantType) {
      case CLIENT -> "Cliente";
      case PRODUCER -> "Productor";
      case BOTH -> "Cliente y productor";
    };
  }
}
