package com.labcore.platform.orders;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.labcore.platform.catalog.CatalogService;
import com.labcore.platform.catalog.Product;
import com.labcore.platform.catalog.ProductVariety;
import com.labcore.platform.common.exception.BusinessRuleViolationException;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class SampleCatalogSelectionTest {
  private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();

  @Test
  void rejectsDecimalAndTextCountsInsteadOfSilentlyTruncating() {
    for (String value : new String[] {"1.5", "\"12e3\"", "\"10\"", "2147483648"}) {
      assertThatThrownBy(
              () ->
                  mapper.readValue(
                      "{\"budCount\":" + value + "}", ServiceOrderDtos.SampleRequest.class))
          .isInstanceOf(com.fasterxml.jackson.core.JsonProcessingException.class);
    }
  }

  @Test
  void acceptsWholeCountsAndRejectsNegativeCountsInTheDomain() throws Exception {
    CatalogService catalogs = mock(CatalogService.class);
    var request =
        mapper.readValue(
            "{\"budCount\":10,\"carrierCount\":2}", ServiceOrderDtos.SampleRequest.class);
    SampleCatalogSelection.validate(request, catalogs);
    assertThat(request.budCount()).isEqualTo(10);
    var negative = mapper.readValue("{\"budCount\":-1}", ServiceOrderDtos.SampleRequest.class);
    assertThatThrownBy(() -> SampleCatalogSelection.validate(negative, catalogs))
        .isInstanceOf(BusinessRuleViolationException.class);
  }

  @Test
  void validatesTheVarietyBelongsToTheSelectedProduct() throws Exception {
    CatalogService catalogs = mock(CatalogService.class);
    UUID productId = UUID.randomUUID();
    UUID varietyId = UUID.randomUUID();
    when(catalogs.getProduct(productId)).thenReturn(new Product("UVA", "Uva"));
    when(catalogs.getVariety(varietyId))
        .thenReturn(new ProductVariety(UUID.randomUUID(), "RED", "Red Globe"));
    var request =
        mapper.readValue(
            "{\"productId\":\""
                + productId
                + "\",\"productName\":\"Uva\",\"varietyId\":\""
                + varietyId
                + "\",\"varietyName\":\"Red Globe\"}",
            ServiceOrderDtos.SampleRequest.class);
    assertThatThrownBy(() -> SampleCatalogSelection.validate(request, catalogs))
        .isInstanceOf(BusinessRuleViolationException.class)
        .hasMessageContaining("pertenecer");
    when(catalogs.getVariety(varietyId))
        .thenReturn(new ProductVariety(productId, "RED", "Red Globe"));
    assertThatCode(() -> SampleCatalogSelection.validate(request, catalogs))
        .doesNotThrowAnyException();
  }
}
