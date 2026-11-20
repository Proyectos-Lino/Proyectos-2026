package com.labcore.platform.results;

import static org.assertj.core.api.Assertions.assertThat;

import com.labcore.platform.catalog.ResultMode;
import com.labcore.platform.catalog.TechnicalCatalogDtos;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class MatrixTemplateConfigurationTest {
  @Test
  void preservesStartedMatrixParametersWhenCatalogChanges() {
    var original = template("Original", 10, "Viable", "Tejido verde", List.of("Recto"));
    var edited = template("Edited", 50, "Otra categoría", "Otra leyenda", List.of("Espiral"));

    var restored =
        MatrixTemplateConfiguration.restore(
            edited, MatrixTemplateConfiguration.serialize(original));

    assertThat(restored.name()).isEqualTo("Edited");
    assertThat(restored.maximumRows()).isEqualTo(10);
    assertThat(restored.carrierShapes()).containsExactly("Recto");
    assertThat(restored.categories()).isEqualTo(original.categories());
  }

  @Test
  void acceptsLegacyTemplatesWithoutConfiguredShapes() {
    var legacy = template("Legacy", 10, "Viable", null, null);
    assertThat(legacy.carrierShapes()).isEmpty();
    assertThat(MatrixTemplateConfiguration.restore(legacy, null)).isSameAs(legacy);
    var restored =
        MatrixTemplateConfiguration.restore(legacy, MatrixTemplateConfiguration.serialize(legacy));
    assertThat(restored.carrierShapes()).isEmpty();
    assertThat(restored.categories().getFirst().legend()).isNull();
  }

  @Test
  void capturesConfigurationOnlyOncePerResult() {
    var entry =
        new ResultEntry(
            UUID.randomUUID(), UUID.randomUUID(), ResultMode.MATRIX, null, UUID.randomUUID());
    entry.captureMatrixConfiguration("original");
    entry.captureMatrixConfiguration("changed");
    assertThat(entry.getMatrixConfigurationSnapshot()).isEqualTo("original");
  }

  private TechnicalCatalogDtos.TemplateResponse template(
      String name, int rows, String label, String legend, List<String> shapes) {
    return new TechnicalCatalogDtos.TemplateResponse(
        UUID.randomUUID(),
        UUID.randomUUID(),
        "Yemas",
        UUID.randomUUID(),
        "Vid",
        name,
        null,
        ResultMode.MATRIX,
        1,
        null,
        2,
        rows,
        1,
        5,
        true,
        Instant.now(),
        List.of(),
        List.of(
            new TechnicalCatalogDtos.TemplateCategoryResponse(
                UUID.randomUUID(), "V", label, 1, legend)),
        shapes);
  }
}
