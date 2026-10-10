package com.labcore.platform.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class ResultTemplateFieldTest {

  @Test
  void capturedFieldsAlwaysUsePerRepetitionScope() {
    ResultTemplateField field =
        new ResultTemplateField(
            UUID.randomUUID(),
            "hum",
            "Humedad",
            ResultDataType.NUMERIC,
            UUID.randomUUID(),
            true,
            0,
            ResultFieldKind.CAPTURED,
            false,
            null,
            CalculationScope.AGGREGATE,
            null,
            null,
            null);

    assertThat(field.getCalculationScope()).isEqualTo(CalculationScope.PER_REPETITION);
    assertThat(field.isReadOnly()).isFalse();
  }
}
