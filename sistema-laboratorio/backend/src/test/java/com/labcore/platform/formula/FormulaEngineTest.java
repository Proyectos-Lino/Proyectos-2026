package com.labcore.platform.formula;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.labcore.platform.catalog.CalculationScope;
import com.labcore.platform.common.exception.BusinessRuleViolationException;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class FormulaEngineTest {
  private final FormulaEngine engine = new FormulaEngine();

  @Test
  void calculatesArithmeticPercentAndRoundingPerRepetition() {
    List<FormulaEngine.Calculation> values =
        engine.calculate(
            List.of(
                definition("subtotal", "cantidad * precio", CalculationScope.PER_REPETITION, 2),
                definition(
                    "porcentaje",
                    "ROUND(PERCENT(parte, total), 1)",
                    CalculationScope.PER_REPETITION,
                    1)),
            Set.of("cantidad", "precio", "parte", "total", "subtotal", "porcentaje"),
            Map.of(
                "cantidad", Map.of(0, new BigDecimal("3")),
                "precio", Map.of(0, new BigDecimal("2.555")),
                "parte", Map.of(0, new BigDecimal("1")),
                "total", Map.of(0, new BigDecimal("3"))));

    assertThat(value(values, "subtotal", 0)).isEqualByComparingTo("7.67");
    assertThat(value(values, "porcentaje", 0)).isEqualByComparingTo("33.3");
  }

  @Test
  void distinguishesPerReadingAndAggregateFunctions() {
    List<FormulaEngine.Calculation> values =
        engine.calculate(
            List.of(
                definition("doble", "SUM(lectura, lectura)", CalculationScope.PER_REPETITION, 0),
                definition("promedio", "AVG(lectura)", CalculationScope.AGGREGATE, 2),
                definition("conteo", "COUNT(lectura)", CalculationScope.AGGREGATE, 0)),
            Set.of("lectura", "doble", "promedio", "conteo"),
            Map.of(
                "lectura",
                Map.of(
                    0, new BigDecimal("4"),
                    1, new BigDecimal("8"),
                    2, new BigDecimal("12"))));

    assertThat(value(values, "doble", 0)).isEqualByComparingTo("8");
    assertThat(value(values, "doble", 1)).isEqualByComparingTo("16");
    assertThat(value(values, "promedio", 0)).isEqualByComparingTo("8.00");
    assertThat(value(values, "conteo", 0)).isEqualByComparingTo("3");
  }

  @Test
  void rejectsMissingReferencesCyclesAndUnknownFunctions() {
    assertThatThrownBy(
            () ->
                engine.validate(
                    List.of(
                        definition("resultado", "ausente + 1", CalculationScope.PER_REPETITION, 2)),
                    Set.of("resultado")))
        .isInstanceOf(BusinessRuleViolationException.class)
        .hasMessageContaining("campo inexistente");

    assertThatThrownBy(
            () ->
                engine.validate(
                    List.of(
                        definition("a", "b + 1", CalculationScope.PER_REPETITION, 2),
                        definition("b", "a + 1", CalculationScope.PER_REPETITION, 2)),
                    Set.of("a", "b")))
        .isInstanceOf(BusinessRuleViolationException.class)
        .hasMessageContaining("dependencia circular");

    assertThatThrownBy(
            () ->
                engine.validate(
                    List.of(
                        definition("resultado", "EXEC(valor)", CalculationScope.PER_REPETITION, 2)),
                    Set.of("resultado", "valor")))
        .isInstanceOf(BusinessRuleViolationException.class)
        .hasMessageContaining("Función no permitida");
  }

  @Test
  void rejectsDivisionByZero() {
    assertThatThrownBy(
            () ->
                engine.calculate(
                    List.of(
                        definition(
                            "resultado",
                            "numerador / divisor",
                            CalculationScope.PER_REPETITION,
                            2)),
                    Set.of("resultado", "numerador", "divisor"),
                    Map.of(
                        "numerador", Map.of(0, BigDecimal.ONE),
                        "divisor", Map.of(0, BigDecimal.ZERO))))
        .isInstanceOf(BusinessRuleViolationException.class)
        .hasMessageContaining("dividir entre cero");
  }

  private FormulaEngine.Definition definition(
      String key, String formula, CalculationScope scope, int precision) {
    return new FormulaEngine.Definition(key, formula, scope, precision);
  }

  private BigDecimal value(List<FormulaEngine.Calculation> values, String key, int repetition) {
    return values.stream()
        .filter(value -> value.fieldKey().equals(key) && value.repetitionIndex() == repetition)
        .findFirst()
        .orElseThrow()
        .value();
  }
}
