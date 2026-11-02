package com.labcore.platform.catalog;

import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/catalogs")
@PreAuthorize("hasAnyRole('ADMIN','COMMERCIAL')")
public class SampleProductController {
  private final CommercialCatalogManagementService service;

  public SampleProductController(CommercialCatalogManagementService service) {
    this.service = service;
  }

  @PostMapping("/products")
  public CommercialCatalogDtos.ProductResponse createProduct(
      @Valid @RequestBody CommercialCatalogDtos.ProductRequest request) {
    return service.createProduct(request);
  }

  @PostMapping("/varieties")
  public CommercialCatalogDtos.VarietyResponse createVariety(
      @Valid @RequestBody CommercialCatalogDtos.VarietyRequest request) {
    return service.createVariety(request);
  }
}
