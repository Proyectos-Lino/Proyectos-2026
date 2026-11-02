package com.labcore.platform.results;

import static org.assertj.core.api.Assertions.*;

import com.labcore.platform.catalog.*;
import com.labcore.platform.common.exception.BusinessRuleViolationException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ResultTemplateValueValidatorTest {
  @Test
  void doesNotTreatAnAggregateOnlyIndexAsAnAdditionalReading() {
    assertThatCode(
            () ->
                ResultTemplateValueValidator.validate(
                    List.of(value("lectura", 1), value("lectura", 2), value("promedio", 0)),
                    template()))
        .doesNotThrowAnyException();
  }

  @Test
  void supportsZeroBasedReadingsAlongsideTheirAggregate() {
    assertThatCode(
            () ->
                ResultTemplateValueValidator.validate(
                    List.of(value("lectura", 0), value("lectura", 1), value("promedio", 0)),
                    template()))
        .doesNotThrowAnyException();
  }

  @Test
  void stillRequiresTheAggregateAndEnforcesActualReadingBounds() {
    assertThatThrownBy(
            () ->
                ResultTemplateValueValidator.validate(
                    List.of(value("lectura", 0), value("lectura", 1)), template()))
        .isInstanceOf(BusinessRuleViolationException.class)
        .hasMessageContaining("globales");
    assertThatThrownBy(
            () ->
                ResultTemplateValueValidator.validate(
                    List.of(
                        value("lectura", 0),
                        value("lectura", 1),
                        value("lectura", 2),
                        value("promedio", 0)),
                    template()))
        .isInstanceOf(BusinessRuleViolationException.class)
        .hasMessageContaining("cantidad de lecturas");
  }

  private ResultDtos.ValueRequest value(String field, int repetition) {
    return new ResultDtos.ValueRequest(field, field, repetition, null, BigDecimal.ONE, null);
  }

  private TechnicalCatalogDtos.TemplateResponse template() {
    return new TechnicalCatalogDtos.TemplateResponse(
        UUID.randomUUID(),
        UUID.randomUUID(),
        "Ensayo",
        null,
        null,
        "Promedio de lecturas",
        null,
        ResultMode.REPEATABLE,
        2,
        2,
        1,
        10,
        1,
        10,
        true,
        Instant.now(),
        List.of(
            new TechnicalCatalogDtos.TemplateFieldResponse(
                UUID.randomUUID(),
                "lectura",
                "Lectura",
                ResultDataType.NUMERIC,
                null,
                true,
                1,
                ResultFieldKind.CAPTURED,
                false,
                null,
                CalculationScope.PER_REPETITION,
                2,
                null,
                null),
            new TechnicalCatalogDtos.TemplateFieldResponse(
                UUID.randomUUID(),
                "promedio",
                "Promedio",
                ResultDataType.NUMERIC,
                null,
                true,
                2,
                ResultFieldKind.CALCULATED,
                true,
                "AVG(lectura)",
                CalculationScope.AGGREGATE,
                2,
                null,
                null)),
        List.of());
  }
}
