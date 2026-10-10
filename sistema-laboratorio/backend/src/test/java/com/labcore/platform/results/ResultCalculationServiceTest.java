package com.labcore.platform.results;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.labcore.platform.catalog.CalculationScope;
import com.labcore.platform.catalog.ResultDataType;
import com.labcore.platform.catalog.ResultFieldKind;
import com.labcore.platform.catalog.ResultMode;
import com.labcore.platform.catalog.TechnicalCatalogDtos;
import com.labcore.platform.common.exception.BusinessRuleViolationException;
import com.labcore.platform.formula.FormulaEngine;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ResultCalculationServiceTest {
  @Mock private ResultCalculationSnapshotRepository snapshotRepository;
  @Mock private ResultValueRepository valueRepository;

  @Test
  void injectsProtectedDefaultsAndCalculatesPerReadingAndAggregateValues() {
    ResultCalculationService.PreparedResult result =
        service().prepare(template(), List.of(value("lectura", 0, "3"), value("lectura", 1, "5")));

    assertThat(result.values()).hasSize(7);
    assertThat(number(result, "ajustado", 0)).isEqualByComparingTo("6.00");
    assertThat(number(result, "ajustado", 1)).isEqualByComparingTo("10.00");
    assertThat(number(result, "promedio", 0)).isEqualByComparingTo("4.00");
    assertThat(result.calculations().getFirst().inputs()).containsKey("factor");
  }

  @Test
  void rejectsCalculatedValuesSubmittedByTheBrowser() {
    assertThatThrownBy(() -> service().prepare(template(), List.of(value("ajustado", 0, "999"))))
        .isInstanceOf(BusinessRuleViolationException.class)
        .hasMessage("Solo se pueden enviar valores para campos capturados editables.");
  }

  private ResultCalculationService service() {
    return new ResultCalculationService(
        new FormulaEngine(), snapshotRepository, valueRepository, new ObjectMapper());
  }

  private TechnicalCatalogDtos.TemplateResponse template() {
    return new TechnicalCatalogDtos.TemplateResponse(
        UUID.randomUUID(),
        UUID.randomUUID(),
        "Ensayo",
        UUID.randomUUID(),
        "Muestra",
        "Plantilla calculada",
        null,
        ResultMode.REPEATABLE,
        2,
        5,
        1,
        null,
        1,
        null,
        true,
        Instant.now(),
        List.of(
            field("lectura", ResultFieldKind.CAPTURED, false, null, null),
            field("factor", ResultFieldKind.CAPTURED, true, null, "2"),
            field("ajustado", ResultFieldKind.CALCULATED, true, "lectura * factor", null),
            new TechnicalCatalogDtos.TemplateFieldResponse(
                UUID.randomUUID(),
                "promedio",
                "Promedio",
                ResultDataType.NUMERIC,
                null,
                true,
                4,
                ResultFieldKind.CALCULATED,
                true,
                "AVG(lectura)",
                CalculationScope.AGGREGATE,
                2,
                null,
                null)),
        List.of());
  }

  private TechnicalCatalogDtos.TemplateFieldResponse field(
      String key, ResultFieldKind kind, boolean readOnly, String formula, String defaultValue) {
    return new TechnicalCatalogDtos.TemplateFieldResponse(
        UUID.randomUUID(),
        key,
        key,
        ResultDataType.NUMERIC,
        null,
        true,
        1,
        kind,
        readOnly,
        formula,
        CalculationScope.PER_REPETITION,
        2,
        null,
        defaultValue);
  }

  private ResultDtos.ValueRequest value(String key, int repetition, String value) {
    return new ResultDtos.ValueRequest(key, key, repetition, null, new BigDecimal(value), null);
  }

  private BigDecimal number(
      ResultCalculationService.PreparedResult result, String key, int repetition) {
    return result.values().stream()
        .filter(value -> value.fieldKey().equals(key) && value.repetitionIndex() == repetition)
        .findFirst()
        .orElseThrow()
        .valueNumber();
  }
}
