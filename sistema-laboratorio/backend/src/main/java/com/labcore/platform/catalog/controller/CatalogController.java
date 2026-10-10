package com.labcore.platform.catalog;

import jakarta.validation.Valid;
import java.net.URI;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/catalogs")
public class CatalogController {

  private final CatalogService catalogService;

  public CatalogController(CatalogService catalogService) {
    this.catalogService = catalogService;
  }

  @GetMapping
  @PreAuthorize("hasAnyRole('ADMIN', 'COMMERCIAL', 'TECHNICAL_MANAGER', 'ANALYST', 'VALIDATOR')")
  public CatalogDtos.CatalogSnapshot getCatalogs() {
    return catalogService.getSnapshot();
  }

  @PostMapping("/areas")
  @PreAuthorize("hasRole('ADMIN')")
  public ResponseEntity<CatalogDtos.Item> createArea(
      @Valid @RequestBody CatalogDtos.AreaRequest request) {
    CatalogDtos.Item response = catalogService.createArea(request);
    return ResponseEntity.created(URI.create("/api/v1/catalogs/areas/" + response.id()))
        .body(response);
  }

  @PostMapping("/units")
  @PreAuthorize("hasRole('ADMIN')")
  public ResponseEntity<CatalogDtos.UnitItem> createUnit(
      @Valid @RequestBody CatalogDtos.UnitRequest request) {
    CatalogDtos.UnitItem response = catalogService.createUnit(request);
    return ResponseEntity.created(URI.create("/api/v1/catalogs/units/" + response.id()))
        .body(response);
  }

  @PostMapping("/sample-types")
  @PreAuthorize("hasRole('ADMIN')")
  public ResponseEntity<CatalogDtos.SampleTypeItem> createSampleType(
      @Valid @RequestBody CatalogDtos.SampleTypeRequest request) {
    CatalogDtos.SampleTypeItem response = catalogService.createSampleType(request);
    return ResponseEntity.created(URI.create("/api/v1/catalogs/sample-types/" + response.id()))
        .body(response);
  }

  @PostMapping("/tests")
  @PreAuthorize("hasRole('ADMIN')")
  public ResponseEntity<CatalogDtos.TestItem> createTest(
      @Valid @RequestBody CatalogDtos.TestRequest request) {
    CatalogDtos.TestItem response = catalogService.createTest(request);
    return ResponseEntity.created(URI.create("/api/v1/catalogs/tests/" + response.id()))
        .body(response);
  }

  @PostMapping("/test-methods")
  @PreAuthorize("hasRole('ADMIN')")
  public ResponseEntity<CatalogDtos.Item> createTestMethod(
      @Valid @RequestBody CatalogDtos.TestMethodRequest request) {
    CatalogDtos.Item response = catalogService.createTestMethod(request);
    return ResponseEntity.created(URI.create("/api/v1/catalogs/test-methods/" + response.id()))
        .body(response);
  }

  @PostMapping("/services")
  @PreAuthorize("hasRole('ADMIN')")
  public ResponseEntity<CatalogDtos.ServiceItem> createService(
      @Valid @RequestBody CatalogDtos.ServiceRequest request) {
    CatalogDtos.ServiceItem response = catalogService.createService(request);
    return ResponseEntity.created(URI.create("/api/v1/catalogs/services/" + response.id()))
        .body(response);
  }
}
