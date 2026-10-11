package com.labcore.platform.results;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.labcore.platform.catalog.CalculationScope;
import com.labcore.platform.catalog.ResultDataType;
import com.labcore.platform.catalog.ResultFieldKind;
import com.labcore.platform.catalog.TechnicalCatalogDtos;
import com.labcore.platform.common.exception.BusinessRuleViolationException;
import com.labcore.platform.formula.FormulaEngine;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

@Service
public class ResultCalculationService {
  private final FormulaEngine formulaEngine;
  private final ResultCalculationSnapshotRepository snapshotRepository;
  private final ResultValueRepository valueRepository;
  private final ObjectMapper objectMapper;

  public ResultCalculationService(
      FormulaEngine formulaEngine,
      ResultCalculationSnapshotRepository snapshotRepository,
      ResultValueRepository valueRepository,
      ObjectMapper objectMapper) {
    this.formulaEngine = formulaEngine;
    this.snapshotRepository = snapshotRepository;
    this.valueRepository = valueRepository;
    this.objectMapper = objectMapper;
  }

  public PreparedResult prepare(
      TechnicalCatalogDtos.TemplateResponse template, List<ResultDtos.ValueRequest> submitted) {
    if (template == null) {
      return new PreparedResult(submitted, List.of());
    }
    Map<String, TechnicalCatalogDtos.TemplateFieldResponse> fields =
        template.fields().stream()
            .collect(
                Collectors.toMap(
                    field -> normalize(field.fieldKey()), field -> field, (left, right) -> left));
    List<ResultDtos.ValueRequest> captured = new ArrayList<>();
    for (ResultDtos.ValueRequest input : submitted) {
      TechnicalCatalogDtos.TemplateFieldResponse field = fields.get(normalize(input.fieldKey()));
      if (field == null || field.fieldKind() != ResultFieldKind.CAPTURED || field.readOnly()) {
        throw new BusinessRuleViolationException(
            "Solo se pueden enviar valores para campos capturados editables.");
      }
      captured.add(input);
    }
    Set<Integer> repetitions =
        captured.stream().map(ResultDtos.ValueRequest::repetitionIndex).collect(Collectors.toSet());
    if (repetitions.isEmpty()) {
      repetitions = Set.of(0);
    }
    for (TechnicalCatalogDtos.TemplateFieldResponse field : template.fields()) {
      if (field.fieldKind() == ResultFieldKind.CAPTURED && field.readOnly()) {
        for (int repetition : repetitions) {
          captured.add(defaultValue(field, repetition));
        }
      }
    }
    Map<String, Map<Integer, BigDecimal>> numericValues = new HashMap<>();
    for (ResultDtos.ValueRequest value : captured) {
      if (value.valueNumber() != null) {
        numericValues
            .computeIfAbsent(normalize(value.fieldKey()), ignored -> new HashMap<>())
            .put(value.repetitionIndex(), value.valueNumber());
      }
    }
    List<FormulaEngine.Calculation> calculations =
        formulaEngine.calculate(definitions(template), fields.keySet(), numericValues);
    for (FormulaEngine.Calculation calculation : calculations) {
      TechnicalCatalogDtos.TemplateFieldResponse field = fields.get(calculation.fieldKey());
      captured.add(
          new ResultDtos.ValueRequest(
              field.fieldKey(),
              field.label(),
              calculation.repetitionIndex(),
              null,
              calculation.value(),
              field.measurementUnitId()));
    }
    return new PreparedResult(List.copyOf(captured), calculations);
  }

  public void persistSnapshots(
      ResultEntry entry,
      int resultRevision,
      List<FormulaEngine.Calculation> calculations,
      UUID actorId,
      String actorRole) {
    if (calculations.isEmpty()) {
      return;
    }
    Map<String, ResultValue> stored =
        valueRepository.findByResultEntryIdOrderByPositionAsc(entry.getId()).stream()
            .collect(
                Collectors.toMap(
                    value -> key(value.getFieldKey(), value.getRepetitionIndex()), value -> value));
    for (FormulaEngine.Calculation calculation : calculations) {
      String inputs = serialize(calculation.inputs());
      ResultValue value = stored.get(key(calculation.fieldKey(), calculation.repetitionIndex()));
      if (value != null) {
        value.markCalculated(calculation.formula(), inputs);
      }
      snapshotRepository.save(
          new ResultCalculationSnapshot(
              entry.getId(),
              calculation.fieldKey(),
              calculation.repetitionIndex(),
              resultRevision,
              calculation.formula(),
              inputs,
              calculation.value(),
              actorId,
              actorRole));
    }
  }

  public List<ResultDtos.CalculationSnapshotResponse> snapshots(UUID resultEntryId) {
    return snapshotRepository
        .findByResultEntryIdOrderByResultRevisionDescFieldKeyAsc(resultEntryId)
        .stream()
        .map(
            snapshot ->
                new ResultDtos.CalculationSnapshotResponse(
                    snapshot.getId(),
                    snapshot.getFieldKey(),
                    snapshot.getRepetitionIndex(),
                    snapshot.getResultRevision(),
                    snapshot.getFormulaUsed(),
                    snapshot.getCalculationInputs(),
                    snapshot.getCalculatedValue(),
                    snapshot.getCalculatedBy(),
                    snapshot.getCalculatedByRole(),
                    snapshot.getCalculatedAt()))
        .toList();
  }

  public ResultDtos.CalculationPreviewResponse preview(
      TechnicalCatalogDtos.TemplateResponse template, List<ResultDtos.ValueRequest> submitted) {
    PreparedResult prepared = prepare(template, submitted);
    Map<String, TechnicalCatalogDtos.TemplateFieldResponse> fields =
        template.fields().stream()
            .collect(Collectors.toMap(field -> normalize(field.fieldKey()), field -> field));
    return new ResultDtos.CalculationPreviewResponse(
        prepared.calculations().stream()
            .map(
                calculation -> {
                  TechnicalCatalogDtos.TemplateFieldResponse field =
                      fields.get(calculation.fieldKey());
                  return new ResultDtos.CalculatedValueResponse(
                      field.fieldKey(),
                      field.label(),
                      calculation.repetitionIndex(),
                      calculation.value(),
                      field.measurementUnitId(),
                      calculation.formula(),
                      calculation.inputs());
                })
            .toList());
  }

  private ResultDtos.ValueRequest defaultValue(
      TechnicalCatalogDtos.TemplateFieldResponse field, int repetition) {
    String configured = field.defaultValue();
    if (configured == null || configured.isBlank()) {
      throw new BusinessRuleViolationException(
          "El campo de solo lectura «%s» no tiene valor predeterminado.".formatted(field.label()));
    }
    if (field.resultType() == ResultDataType.NUMERIC) {
      try {
        return new ResultDtos.ValueRequest(
            field.fieldKey(),
            field.label(),
            repetition,
            null,
            new BigDecimal(configured.trim()),
            field.measurementUnitId());
      } catch (NumberFormatException exception) {
        throw new BusinessRuleViolationException(
            "El valor predeterminado de «%s» debe ser numérico.".formatted(field.label()));
      }
    }
    return new ResultDtos.ValueRequest(
        field.fieldKey(), field.label(), repetition, configured.trim(), null, null);
  }

  private List<FormulaEngine.Definition> definitions(
      TechnicalCatalogDtos.TemplateResponse template) {
    return template.fields().stream()
        .filter(field -> field.fieldKind() == ResultFieldKind.CALCULATED)
        .map(
            field ->
                new FormulaEngine.Definition(
                    field.fieldKey(),
                    field.formula(),
                    field.calculationScope() == null
                        ? CalculationScope.PER_REPETITION
                        : field.calculationScope(),
                    field.decimalPrecision()))
        .toList();
  }

  private String serialize(Map<String, List<BigDecimal>> inputs) {
    try {
      return objectMapper.writeValueAsString(inputs);
    } catch (JsonProcessingException exception) {
      throw new IllegalStateException("No se pudo serializar el snapshot del cálculo.", exception);
    }
  }

  private String normalize(String value) {
    return value.trim().toLowerCase(Locale.ROOT);
  }

  private String key(String fieldKey, int repetition) {
    return normalize(fieldKey) + ":" + repetition;
  }

  public record PreparedResult(
      List<ResultDtos.ValueRequest> values, List<FormulaEngine.Calculation> calculations) {}
}
