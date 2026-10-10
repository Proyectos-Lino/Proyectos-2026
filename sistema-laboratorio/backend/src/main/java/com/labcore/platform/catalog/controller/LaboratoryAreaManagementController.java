package com.labcore.platform.catalog;

import com.labcore.platform.common.web.PageResponse;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
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
@RequestMapping("/api/v1/laboratory-areas")
@PreAuthorize("hasRole('ADMIN')")
public class LaboratoryAreaManagementController {

  private final LaboratoryAreaManagementService service;

  public LaboratoryAreaManagementController(LaboratoryAreaManagementService service) {
    this.service = service;
  }

  @GetMapping
  public PageResponse<LaboratoryAreaDtos.Response> list(
      @RequestParam(required = false) String search,
      @RequestParam(required = false) Boolean active,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    return PageResponse.from(
        service.findPage(
            search,
            active,
            PageRequest.of(
                Math.max(0, page),
                Math.min(Math.max(1, size), 100),
                Sort.by(Sort.Direction.ASC, "name"))));
  }

  @GetMapping("/options")
  public List<LaboratoryAreaDtos.Option> options() {
    return service.listActiveOptions();
  }

  @PostMapping
  public ResponseEntity<LaboratoryAreaDtos.Response> create(
      @Valid @RequestBody LaboratoryAreaDtos.CreateRequest request) {
    LaboratoryAreaDtos.Response response = service.create(request);
    return ResponseEntity.created(URI.create("/api/v1/laboratory-areas/" + response.id()))
        .body(response);
  }

  @PutMapping("/{areaId}")
  public LaboratoryAreaDtos.Response update(
      @PathVariable UUID areaId, @Valid @RequestBody LaboratoryAreaDtos.UpdateRequest request) {
    return service.update(areaId, request);
  }

  @PatchMapping("/{areaId}/status")
  public LaboratoryAreaDtos.Response updateStatus(
      @PathVariable UUID areaId,
      @Valid @RequestBody LaboratoryAreaDtos.UpdateStatusRequest request) {
    return service.updateStatus(areaId, request.active());
  }
}
