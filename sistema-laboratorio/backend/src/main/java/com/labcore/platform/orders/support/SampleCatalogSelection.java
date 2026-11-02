package com.labcore.platform.orders;

import com.labcore.platform.catalog.CatalogService;
import com.labcore.platform.common.exception.BusinessRuleViolationException;

public final class SampleCatalogSelection {
  private SampleCatalogSelection() {}

  public static void validate(ServiceOrderDtos.SampleRequest request, CatalogService catalogs) {
    if ((request.budCount() != null && request.budCount() < 0)
        || (request.carrierCount() != null && request.carrierCount() < 0)) {
      throw new BusinessRuleViolationException(
          "Las cantidades de yemas y cargadores deben ser enteros no negativos.");
    }
    if (request.productId() != null) {
      var product = catalogs.getProduct(request.productId());
      if (!product.getName().equals(request.productName().trim())) {
        throw new BusinessRuleViolationException(
            "El producto seleccionado no coincide con su nombre en el catálogo.");
      }
    }
    if (request.varietyId() != null) {
      var variety = catalogs.getVariety(request.varietyId());
      if (!variety.getProductId().equals(request.productId())
          || request.varietyName() == null
          || !variety.getName().equals(request.varietyName().trim())) {
        throw new BusinessRuleViolationException(
            "La variedad seleccionada debe pertenecer al producto indicado.");
      }
    } else if (request.productId() != null
        && request.varietyName() != null
        && !request.varietyName().isBlank()) {
      throw new BusinessRuleViolationException(
          "Selecciona una variedad registrada para el producto.");
    }
  }
}
