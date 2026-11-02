package com.labcore.platform.results;

import com.labcore.platform.catalog.CalculationScope;
import com.labcore.platform.catalog.ResultFieldKind;
import com.labcore.platform.catalog.ResultMode;
import com.labcore.platform.catalog.TechnicalCatalogDtos;
import com.labcore.platform.common.exception.BusinessRuleViolationException;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public final class ResultTemplateValueValidator {
  private ResultTemplateValueValidator() {}

  public static void validate(
      List<ResultDtos.ValueRequest> values, TechnicalCatalogDtos.TemplateResponse template) {
    Set<String> aggregates =
        template.fields().stream()
            .filter(
                field ->
                    field.fieldKind() == ResultFieldKind.CALCULATED
                        && field.calculationScope() == CalculationScope.AGGREGATE)
            .map(field -> key(field.fieldKey()))
            .collect(Collectors.toSet());
    Set<String> requiredPerReading =
        template.fields().stream()
            .filter(TechnicalCatalogDtos.TemplateFieldResponse::required)
            .filter(field -> field.fieldKind() != ResultFieldKind.INFORMATIONAL)
            .map(field -> key(field.fieldKey()))
            .filter(field -> !aggregates.contains(field))
            .collect(Collectors.toSet());
    Map<Integer, Set<String>> readings =
        values.stream()
            .filter(value -> !aggregates.contains(key(value.fieldKey())))
            .collect(
                Collectors.groupingBy(
                    ResultDtos.ValueRequest::repetitionIndex,
                    Collectors.mapping(value -> key(value.fieldKey()), Collectors.toSet())));
    if (!requiredPerReading.isEmpty() && readings.isEmpty()
        || readings.values().stream().anyMatch(fields -> !fields.containsAll(requiredPerReading)))
      throw new BusinessRuleViolationException(
          "Cada lectura debe incluir todos los campos obligatorios de la plantilla.");
    Set<String> globalValues =
        values.stream()
            .filter(value -> value.repetitionIndex() == 0)
            .map(value -> key(value.fieldKey()))
            .collect(Collectors.toSet());
    boolean missingAggregate =
        template.fields().stream()
            .filter(TechnicalCatalogDtos.TemplateFieldResponse::required)
            .map(field -> key(field.fieldKey()))
            .filter(aggregates::contains)
            .anyMatch(field -> !globalValues.contains(field));
    if (missingAggregate)
      throw new BusinessRuleViolationException(
          "Falta uno o más resultados calculados globales obligatorios.");
    if (template.resultMode() == ResultMode.REPEATABLE
        && (readings.size() < template.minimumRepetitions()
            || template.maximumRepetitions() != null
                && readings.size() > template.maximumRepetitions()))
      throw new BusinessRuleViolationException(
          "La cantidad de lecturas no cumple los límites configurados en la plantilla.");
  }

  private static String key(String value) {
    return value.trim().toLowerCase(Locale.ROOT);
  }
}
