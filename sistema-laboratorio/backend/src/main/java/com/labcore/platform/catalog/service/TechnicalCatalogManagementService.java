package com.labcore.platform.catalog;

import com.labcore.platform.common.exception.BusinessRuleViolationException;
import com.labcore.platform.common.exception.ResourceNotFoundException;
import com.labcore.platform.formula.FormulaEngine;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class TechnicalCatalogManagementService {

  private final MeasurementUnitRepository unitRepository;
  private final SampleTypeRepository sampleTypeRepository;
  private final TestDefinitionRepository testRepository;
  private final TestMethodRepository methodRepository;
  private final ResultTemplateRepository templateRepository;
  private final ResultTemplateFieldRepository templateFieldRepository;
  private final ResultTemplateCategoryRepository templateCategoryRepository;
  private final FormulaEngine formulaEngine;

  public TechnicalCatalogManagementService(
      MeasurementUnitRepository unitRepository,
      SampleTypeRepository sampleTypeRepository,
      TestDefinitionRepository testRepository,
      TestMethodRepository methodRepository,
      ResultTemplateRepository templateRepository,
      ResultTemplateFieldRepository templateFieldRepository,
      ResultTemplateCategoryRepository templateCategoryRepository,
      FormulaEngine formulaEngine) {
    this.unitRepository = unitRepository;
    this.sampleTypeRepository = sampleTypeRepository;
    this.testRepository = testRepository;
    this.methodRepository = methodRepository;
    this.templateRepository = templateRepository;
    this.templateFieldRepository = templateFieldRepository;
    this.templateCategoryRepository = templateCategoryRepository;
    this.formulaEngine = formulaEngine;
  }

  public TechnicalCatalogDtos.UnitResponse createUnit(TechnicalCatalogDtos.UnitRequest request) {
    String code = normalizeCode(request.code());
    if (unitRepository.existsByCodeIgnoreCase(code)) {
      throw duplicateCode("unidad de medida", code);
    }
    return toUnit(
        unitRepository.save(
            new MeasurementUnit(code, request.name().trim(), request.symbol().trim())));
  }

  public TechnicalCatalogDtos.UnitResponse updateUnit(
      UUID id, TechnicalCatalogDtos.UnitRequest request) {
    MeasurementUnit unit = requiredUnit(id);
    String code = normalizeCode(request.code());
    if (unitRepository.existsByCodeIgnoreCaseAndIdNot(code, id)) {
      throw duplicateCode("unidad de medida", code);
    }
    unit.update(code, request.name().trim(), request.symbol().trim());
    return toUnit(unit);
  }

  public TechnicalCatalogDtos.UnitResponse updateUnitStatus(
      UUID id, TechnicalCatalogDtos.StatusRequest request) {
    MeasurementUnit unit = requiredUnit(id);
    setStatus(unit, request.active());
    return toUnit(unit);
  }

  @Transactional(readOnly = true)
  public Page<TechnicalCatalogDtos.UnitResponse> findUnits(
      String search, Boolean active, Pageable pageable) {
    return unitRepository
        .findAll(
            CatalogSpecifications.<MeasurementUnit>textSearch(search, "code", "name", "symbol")
                .and(CatalogSpecifications.active(active)),
            pageable)
        .map(this::toUnit);
  }

  public TechnicalCatalogDtos.SampleTypeResponse createSampleType(
      TechnicalCatalogDtos.SampleTypeRequest request) {
    String code = normalizeCode(request.code());
    if (sampleTypeRepository.existsByCodeIgnoreCase(code)) {
      throw duplicateCode("tipo de muestra", code);
    }
    return toSampleType(
        sampleTypeRepository.save(
            new SampleType(
                code,
                request.name().trim(),
                normalizeCode(request.laboratoryCodePrefix()),
                request.resultMode())));
  }

  public TechnicalCatalogDtos.SampleTypeResponse updateSampleType(
      UUID id, TechnicalCatalogDtos.SampleTypeRequest request) {
    SampleType sampleType = requiredSampleType(id);
    String code = normalizeCode(request.code());
    if (sampleTypeRepository.existsByCodeIgnoreCaseAndIdNot(code, id)) {
      throw duplicateCode("tipo de muestra", code);
    }
    sampleType.update(
        code,
        request.name().trim(),
        normalizeCode(request.laboratoryCodePrefix()),
        request.resultMode());
    return toSampleType(sampleType);
  }

  public TechnicalCatalogDtos.SampleTypeResponse updateSampleTypeStatus(
      UUID id, TechnicalCatalogDtos.StatusRequest request) {
    SampleType sampleType = requiredSampleType(id);
    setStatus(sampleType, request.active());
    return toSampleType(sampleType);
  }

  @Transactional(readOnly = true)
  public Page<TechnicalCatalogDtos.SampleTypeResponse> findSampleTypes(
      String search, Boolean active, Pageable pageable) {
    return sampleTypeRepository
        .findAll(
            CatalogSpecifications.<SampleType>textSearch(
                    search, "code", "name", "laboratoryCodePrefix")
                .and(CatalogSpecifications.active(active)),
            pageable)
        .map(this::toSampleType);
  }

  public TechnicalCatalogDtos.TestResponse createTest(TechnicalCatalogDtos.TestRequest request) {
    String code = normalizeCode(request.code());
    if (testRepository.existsByCodeIgnoreCase(code)) {
      throw duplicateCode("ensayo", code);
    }
    UUID unitId = resolveUnitForResultType(request.resultType(), request.resultUnitId());
    return toTest(
        testRepository.save(
            new TestDefinition(code, request.name().trim(), request.resultType(), unitId)));
  }

  public TechnicalCatalogDtos.TestResponse updateTest(
      UUID id, TechnicalCatalogDtos.TestRequest request) {
    TestDefinition test = requiredTest(id);
    String code = normalizeCode(request.code());
    if (testRepository.existsByCodeIgnoreCaseAndIdNot(code, id)) {
      throw duplicateCode("ensayo", code);
    }
    test.update(
        code,
        request.name().trim(),
        request.resultType(),
        resolveUnitForResultType(request.resultType(), request.resultUnitId()));
    return toTest(test);
  }

  public TechnicalCatalogDtos.TestResponse updateTestStatus(
      UUID id, TechnicalCatalogDtos.StatusRequest request) {
    TestDefinition test = requiredTest(id);
    setStatus(test, request.active());
    return toTest(test);
  }

  @Transactional(readOnly = true)
  public Page<TechnicalCatalogDtos.TestResponse> findTests(
      String search, Boolean active, Pageable pageable) {
    return testRepository
        .findAll(
            CatalogSpecifications.<TestDefinition>textSearch(search, "code", "name")
                .and(CatalogSpecifications.active(active)),
            pageable)
        .map(this::toTest);
  }

  public TechnicalCatalogDtos.MethodResponse createMethod(
      TechnicalCatalogDtos.MethodRequest request) {
    TestDefinition test = requiredActiveTest(request.testDefinitionId());
    String code = normalizeCode(request.code());
    if (methodRepository.existsByTestDefinitionIdAndCodeIgnoreCase(test.getId(), code)) {
      throw new BusinessRuleViolationException(
          "Ya existe un método con ese código para el ensayo.");
    }
    return toMethod(
        methodRepository.save(
            new TestMethod(
                test.getId(), code, request.name().trim(), nullableTrim(request.versionLabel()))));
  }

  public TechnicalCatalogDtos.MethodResponse updateMethod(
      UUID id, TechnicalCatalogDtos.MethodRequest request) {
    TestMethod method = requiredMethod(id);
    if (!method.getTestDefinitionId().equals(request.testDefinitionId())) {
      throw new BusinessRuleViolationException(
          "No es posible cambiar el ensayo asociado a un método.");
    }
    String code = normalizeCode(request.code());
    if (methodRepository.existsByTestDefinitionIdAndCodeIgnoreCaseAndIdNot(
        method.getTestDefinitionId(), code, id)) {
      throw new BusinessRuleViolationException(
          "Ya existe un método con ese código para el ensayo.");
    }
    method.update(code, request.name().trim(), nullableTrim(request.versionLabel()));
    return toMethod(method);
  }

  public TechnicalCatalogDtos.MethodResponse updateMethodStatus(
      UUID id, TechnicalCatalogDtos.StatusRequest request) {
    TestMethod method = requiredMethod(id);
    setStatus(method, request.active());
    return toMethod(method);
  }

  @Transactional(readOnly = true)
  public Page<TechnicalCatalogDtos.MethodResponse> findMethods(
      String search, Boolean active, UUID testDefinitionId, Pageable pageable) {
    Specification<TestMethod> specification =
        CatalogSpecifications.<TestMethod>textSearch(search, "code", "name", "versionLabel")
            .and(CatalogSpecifications.active(active));
    if (testDefinitionId != null) {
      specification =
          specification.and(CatalogSpecifications.equalTo("testDefinitionId", testDefinitionId));
    }
    return methodRepository.findAll(specification, pageable).map(this::toMethod);
  }

  public TechnicalCatalogDtos.TemplateResponse createTemplate(
      TechnicalCatalogDtos.TemplateRequest request) {
    TestDefinition test = requiredActiveTest(request.testDefinitionId());
    validateSampleType(request.sampleTypeId());
    String name = request.name().trim();
    if (templateNameExists(test.getId(), request.sampleTypeId(), name, null)) {
      throw new BusinessRuleViolationException(
          "Ya existe una plantilla con ese nombre para el ensayo y tipo de muestra.");
    }
    validateTemplateConfiguration(request);
    validateTemplateFields(request.fields());
    ResultTemplate template =
        templateRepository.save(
            new ResultTemplate(
                test.getId(),
                request.sampleTypeId(),
                name,
                request.description(),
                request.resultMode()));
    updateTemplateConfiguration(template, request);
    replaceTemplateFields(template.getId(), request.fields());
    replaceTemplateCategories(template.getId(), request.categories());
    return toTemplate(template);
  }

  public TechnicalCatalogDtos.TemplateResponse updateTemplate(
      UUID id, TechnicalCatalogDtos.TemplateRequest request) {
    ResultTemplate template = requiredTemplate(id);
    if (!template.getTestDefinitionId().equals(request.testDefinitionId())) {
      throw new BusinessRuleViolationException(
          "No es posible cambiar el ensayo asociado a una plantilla.");
    }
    if (!java.util.Objects.equals(template.getSampleTypeId(), request.sampleTypeId())) {
      throw new BusinessRuleViolationException(
          "No es posible cambiar el tipo de muestra asociado a una plantilla.");
    }
    validateSampleType(request.sampleTypeId());
    String name = request.name().trim();
    if (templateNameExists(template.getTestDefinitionId(), template.getSampleTypeId(), name, id)) {
      throw new BusinessRuleViolationException(
          "Ya existe una plantilla con ese nombre para el ensayo y tipo de muestra.");
    }
    validateTemplateConfiguration(request);
    validateTemplateFields(request.fields());
    updateTemplateConfiguration(template, request);
    templateFieldRepository.deleteAllInBatch(
        templateFieldRepository.findByResultTemplateIdOrderByDisplayOrderAsc(id));
    templateCategoryRepository.deleteAllInBatch(
        templateCategoryRepository.findByResultTemplateIdOrderByDisplayOrderAsc(id));
    replaceTemplateFields(id, request.fields());
    replaceTemplateCategories(id, request.categories());
    return toTemplate(template);
  }

  public TechnicalCatalogDtos.TemplateResponse updateTemplateStatus(
      UUID id, TechnicalCatalogDtos.StatusRequest request) {
    ResultTemplate template = requiredTemplate(id);
    setStatus(template, request.active());
    return toTemplate(template);
  }

  @Transactional(readOnly = true)
  public Page<TechnicalCatalogDtos.TemplateResponse> findTemplates(
      String search, Boolean active, UUID testDefinitionId, Pageable pageable) {
    Specification<ResultTemplate> specification =
        CatalogSpecifications.<ResultTemplate>textSearch(search, "name", "description")
            .and(CatalogSpecifications.active(active));
    if (testDefinitionId != null) {
      specification =
          specification.and(CatalogSpecifications.equalTo("testDefinitionId", testDefinitionId));
    }
    return templateRepository.findAll(specification, pageable).map(this::toTemplate);
  }

  @Transactional(readOnly = true)
  public TechnicalCatalogDtos.TemplateResponse findInitialTemplate(
      UUID testDefinitionId, UUID sampleTypeId) {
    return templateRepository
        .findFirstByTestDefinitionIdAndSampleTypeIdAndActiveTrueOrderByCreatedAtAsc(
            testDefinitionId, sampleTypeId)
        .or(
            () ->
                templateRepository
                    .findFirstByTestDefinitionIdAndSampleTypeIdIsNullAndActiveTrueOrderByCreatedAtAsc(
                        testDefinitionId))
        .map(this::toTemplate)
        .orElse(null);
  }

  @Transactional(readOnly = true)
  public TechnicalCatalogDtos.TemplateResponse getTemplate(UUID templateId) {
    return toTemplate(requiredTemplate(templateId));
  }

  private void replaceTemplateFields(
      UUID templateId, java.util.List<TechnicalCatalogDtos.TemplateFieldRequest> fields) {
    for (TechnicalCatalogDtos.TemplateFieldRequest field : fields) {
      UUID unitId = resolveUnitForResultType(field.resultType(), field.measurementUnitId());
      templateFieldRepository.save(
          new ResultTemplateField(
              templateId,
              field.fieldKey(),
              field.label(),
              field.resultType(),
              unitId,
              field.required(),
              field.displayOrder(),
              field.fieldKind(),
              field.readOnly(),
              nullableTrim(field.formula()),
              field.calculationScope(),
              field.decimalPrecision(),
              nullableTrim(field.informationText()),
              nullableTrim(field.defaultValue())));
    }
  }

  private void replaceTemplateCategories(
      UUID templateId, java.util.List<TechnicalCatalogDtos.TemplateCategoryRequest> categories) {
    for (TechnicalCatalogDtos.TemplateCategoryRequest category : categories) {
      templateCategoryRepository.save(
          new ResultTemplateCategory(
              templateId, category.code(), category.label(), category.displayOrder()));
    }
  }

  private void updateTemplateConfiguration(
      ResultTemplate template, TechnicalCatalogDtos.TemplateRequest request) {
    template.update(
        request.name(),
        request.description(),
        request.resultMode(),
        request.minimumRepetitions(),
        request.maximumRepetitions(),
        request.defaultRows(),
        request.maximumRows(),
        request.defaultColumns(),
        request.maximumColumns());
  }

  private void validateTemplateFields(
      java.util.List<TechnicalCatalogDtos.TemplateFieldRequest> fields) {
    Set<String> fieldKeys = new HashSet<>();
    Map<String, TechnicalCatalogDtos.TemplateFieldRequest> fieldsByKey = new HashMap<>();
    for (TechnicalCatalogDtos.TemplateFieldRequest field : fields) {
      String key = field.fieldKey().trim().toLowerCase(Locale.ROOT);
      if (!fieldKeys.add(key)) {
        throw new BusinessRuleViolationException(
            "Cada campo de la plantilla debe tener una clave única.");
      }
      if (!key.matches("[a-z][a-z0-9_]{0,79}")) {
        throw new BusinessRuleViolationException(
            "Las claves de campo deben iniciar con una letra y usar solo minúsculas, números o guion bajo.");
      }
      validateFieldConfiguration(field);
      fieldsByKey.put(key, field);
    }
    formulaEngine.validate(
        fields.stream()
            .filter(field -> field.fieldKind() == ResultFieldKind.CALCULATED)
            .map(
                field ->
                    new FormulaEngine.Definition(
                        field.fieldKey(),
                        field.formula(),
                        field.calculationScope(),
                        field.decimalPrecision()))
            .toList(),
        fieldKeys);
    fields.stream()
        .filter(field -> field.fieldKind() == ResultFieldKind.CALCULATED)
        .forEach(
            calculated ->
                formulaEngine
                    .references(calculated.formula())
                    .forEach(
                        reference -> {
                          TechnicalCatalogDtos.TemplateFieldRequest referenced =
                              fieldsByKey.get(reference);
                          if (referenced != null
                              && (referenced.resultType() != ResultDataType.NUMERIC
                                  || referenced.fieldKind() == ResultFieldKind.INFORMATIONAL)) {
                            throw new BusinessRuleViolationException(
                                "La fórmula de «%s» referencia «%s», que no es un campo numérico."
                                    .formatted(calculated.fieldKey(), reference));
                          }
                        }));
  }

  public TechnicalCatalogDtos.FormulaPreviewResponse previewFormula(
      TechnicalCatalogDtos.FormulaPreviewRequest request) {
    validateTemplateFields(request.fields());
    Map<String, Map<Integer, java.math.BigDecimal>> values = new HashMap<>();
    request
        .sampleValues()
        .forEach(
            (key, readings) -> {
              Map<Integer, java.math.BigDecimal> indexed = new HashMap<>();
              for (int index = 0; index < readings.size(); index++) {
                indexed.put(index, readings.get(index));
              }
              values.put(key, indexed);
            });
    List<FormulaEngine.Calculation> calculations =
        formulaEngine.calculate(
            formulaDefinitions(request.fields()),
            request.fields().stream()
                .map(field -> field.fieldKey().trim().toLowerCase(Locale.ROOT))
                .collect(Collectors.toSet()),
            values);
    return new TechnicalCatalogDtos.FormulaPreviewResponse(
        calculations.stream()
            .map(
                value ->
                    new TechnicalCatalogDtos.FormulaPreviewValue(
                        value.fieldKey(),
                        value.repetitionIndex(),
                        value.formula(),
                        value.inputs(),
                        value.value()))
            .toList());
  }

  private List<FormulaEngine.Definition> formulaDefinitions(
      List<TechnicalCatalogDtos.TemplateFieldRequest> fields) {
    return fields.stream()
        .filter(field -> field.fieldKind() == ResultFieldKind.CALCULATED)
        .map(
            field ->
                new FormulaEngine.Definition(
                    field.fieldKey(),
                    field.formula(),
                    field.calculationScope(),
                    field.decimalPrecision()))
        .toList();
  }

  private void validateFieldConfiguration(TechnicalCatalogDtos.TemplateFieldRequest field) {
    if (field.fieldKind() == ResultFieldKind.CALCULATED) {
      if (field.resultType() != ResultDataType.NUMERIC) {
        throw new BusinessRuleViolationException("Los campos calculados deben ser numéricos.");
      }
      if (nullableTrim(field.formula()) == null) {
        throw new BusinessRuleViolationException("Todo campo calculado requiere una fórmula.");
      }
    }
    if (field.fieldKind() == ResultFieldKind.INFORMATIONAL
        && nullableTrim(field.informationText()) == null) {
      throw new BusinessRuleViolationException("Todo campo informativo requiere un texto.");
    }
    if (field.fieldKind() == ResultFieldKind.CAPTURED
        && field.readOnly()
        && nullableTrim(field.defaultValue()) == null) {
      throw new BusinessRuleViolationException(
          "Un campo capturado de solo lectura requiere un valor predeterminado.");
    }
    if (field.fieldKind() == ResultFieldKind.CAPTURED
        && field.readOnly()
        && field.resultType() == ResultDataType.NUMERIC) {
      try {
        new java.math.BigDecimal(field.defaultValue().trim());
      } catch (NumberFormatException exception) {
        throw new BusinessRuleViolationException(
            "El valor predeterminado de un campo numérico debe ser válido.");
      }
    }
  }

  private void validateTemplateConfiguration(TechnicalCatalogDtos.TemplateRequest request) {
    validateRange(
        request.minimumRepetitions(),
        request.maximumRepetitions(),
        "El máximo de lecturas no puede ser menor que el mínimo.");
    validateRange(
        request.defaultRows(),
        request.maximumRows(),
        "El máximo de filas no puede ser menor que las filas iniciales.");
    validateRange(
        request.defaultColumns(),
        request.maximumColumns(),
        "El máximo de columnas no puede ser menor que las columnas iniciales.");
    Set<String> categoryCodes = new HashSet<>();
    for (TechnicalCatalogDtos.TemplateCategoryRequest category : request.categories()) {
      if (!categoryCodes.add(normalizeCode(category.code()))) {
        throw new BusinessRuleViolationException(
            "Cada categoría de matriz debe tener un código único.");
      }
    }
    if (request.resultMode() != ResultMode.MATRIX && !request.categories().isEmpty()) {
      throw new BusinessRuleViolationException(
          "Las categorías solo pueden configurarse en plantillas matriciales.");
    }
    if (request.resultMode() == ResultMode.MATRIX
        && request.fields().stream()
            .anyMatch(field -> field.fieldKind() == ResultFieldKind.CALCULATED)) {
      throw new BusinessRuleViolationException(
          "Los campos calculados se admiten en resultados escalares o repetibles, no en matrices.");
    }
  }

  private void validateRange(int minimum, Integer maximum, String message) {
    if (maximum != null && maximum < minimum) {
      throw new BusinessRuleViolationException(message);
    }
  }

  private void validateSampleType(UUID sampleTypeId) {
    if (sampleTypeId == null) {
      return;
    }
    SampleType sampleType = requiredSampleType(sampleTypeId);
    if (!sampleType.isActive()) {
      throw new BusinessRuleViolationException(
          "El tipo de muestra seleccionado se encuentra inactivo.");
    }
  }

  private boolean templateNameExists(
      UUID testDefinitionId, UUID sampleTypeId, String name, UUID excludedId) {
    if (sampleTypeId == null) {
      return excludedId == null
          ? templateRepository.existsByTestDefinitionIdAndSampleTypeIdIsNullAndNameIgnoreCase(
              testDefinitionId, name)
          : templateRepository
              .existsByTestDefinitionIdAndSampleTypeIdIsNullAndNameIgnoreCaseAndIdNot(
                  testDefinitionId, name, excludedId);
    }
    return excludedId == null
        ? templateRepository.existsByTestDefinitionIdAndSampleTypeIdAndNameIgnoreCase(
            testDefinitionId, sampleTypeId, name)
        : templateRepository.existsByTestDefinitionIdAndSampleTypeIdAndNameIgnoreCaseAndIdNot(
            testDefinitionId, sampleTypeId, name, excludedId);
  }

  private UUID resolveUnitForResultType(ResultDataType resultType, UUID unitId) {
    if (resultType != ResultDataType.NUMERIC) {
      return null;
    }
    if (unitId == null) {
      return null;
    }
    MeasurementUnit unit = requiredUnit(unitId);
    if (!unit.isActive()) {
      throw new BusinessRuleViolationException(
          "La unidad de medida seleccionada se encuentra inactiva.");
    }
    return unit.getId();
  }

  private MeasurementUnit requiredUnit(UUID id) {
    return unitRepository
        .findById(id)
        .orElseThrow(() -> new ResourceNotFoundException("la unidad de medida", id));
  }

  private SampleType requiredSampleType(UUID id) {
    return sampleTypeRepository
        .findById(id)
        .orElseThrow(() -> new ResourceNotFoundException("el tipo de muestra", id));
  }

  private TestDefinition requiredTest(UUID id) {
    return testRepository
        .findById(id)
        .orElseThrow(() -> new ResourceNotFoundException("el ensayo", id));
  }

  private TestDefinition requiredActiveTest(UUID id) {
    TestDefinition test = requiredTest(id);
    if (!test.isActive()) {
      throw new BusinessRuleViolationException("El ensayo seleccionado se encuentra inactivo.");
    }
    return test;
  }

  private TestMethod requiredMethod(UUID id) {
    return methodRepository
        .findById(id)
        .orElseThrow(() -> new ResourceNotFoundException("el método", id));
  }

  private ResultTemplate requiredTemplate(UUID id) {
    return templateRepository
        .findById(id)
        .orElseThrow(() -> new ResourceNotFoundException("la plantilla de resultados", id));
  }

  private TechnicalCatalogDtos.UnitResponse toUnit(MeasurementUnit unit) {
    return new TechnicalCatalogDtos.UnitResponse(
        unit.getId(),
        unit.getCode(),
        unit.getName(),
        unit.getSymbol(),
        unit.isActive(),
        unit.getCreatedAt());
  }

  private TechnicalCatalogDtos.SampleTypeResponse toSampleType(SampleType sampleType) {
    return new TechnicalCatalogDtos.SampleTypeResponse(
        sampleType.getId(),
        sampleType.getCode(),
        sampleType.getName(),
        sampleType.getLaboratoryCodePrefix(),
        sampleType.getResultMode(),
        sampleType.isActive(),
        sampleType.getCreatedAt());
  }

  private TechnicalCatalogDtos.TestResponse toTest(TestDefinition test) {
    return new TechnicalCatalogDtos.TestResponse(
        test.getId(),
        test.getCode(),
        test.getName(),
        test.getResultType(),
        test.getResultUnitId(),
        test.isActive(),
        test.getCreatedAt());
  }

  private TechnicalCatalogDtos.MethodResponse toMethod(TestMethod method) {
    return new TechnicalCatalogDtos.MethodResponse(
        method.getId(),
        method.getTestDefinitionId(),
        method.getCode(),
        method.getName(),
        method.getVersionLabel(),
        method.isActive(),
        method.getCreatedAt());
  }

  private TechnicalCatalogDtos.TemplateResponse toTemplate(ResultTemplate template) {
    TestDefinition test = requiredTest(template.getTestDefinitionId());
    String sampleTypeName =
        template.getSampleTypeId() == null
            ? null
            : requiredSampleType(template.getSampleTypeId()).getName();
    return new TechnicalCatalogDtos.TemplateResponse(
        template.getId(),
        template.getTestDefinitionId(),
        test.getName(),
        template.getSampleTypeId(),
        sampleTypeName,
        template.getName(),
        template.getDescription(),
        template.getResultMode(),
        template.getMinimumRepetitions(),
        template.getMaximumRepetitions(),
        template.getDefaultRows(),
        template.getMaximumRows(),
        template.getDefaultColumns(),
        template.getMaximumColumns(),
        template.isActive(),
        template.getCreatedAt(),
        templateFieldRepository
            .findByResultTemplateIdOrderByDisplayOrderAsc(template.getId())
            .stream()
            .map(
                field ->
                    new TechnicalCatalogDtos.TemplateFieldResponse(
                        field.getId(),
                        field.getFieldKey(),
                        field.getLabel(),
                        field.getResultType(),
                        field.getMeasurementUnitId(),
                        field.isRequired(),
                        field.getDisplayOrder(),
                        field.getFieldKind(),
                        field.isReadOnly(),
                        field.getFormula(),
                        field.getCalculationScope(),
                        field.getDecimalPrecision(),
                        field.getInformationText(),
                        field.getDefaultValue()))
            .toList(),
        templateCategoryRepository
            .findByResultTemplateIdOrderByDisplayOrderAsc(template.getId())
            .stream()
            .map(
                category ->
                    new TechnicalCatalogDtos.TemplateCategoryResponse(
                        category.getId(),
                        category.getCode(),
                        category.getLabel(),
                        category.getDisplayOrder()))
            .toList());
  }

  private BusinessRuleViolationException duplicateCode(String resource, String code) {
    return new BusinessRuleViolationException(
        "Ya existe %s con el código %s.".formatted(resource, code));
  }

  private String normalizeCode(String value) {
    return value.trim().toUpperCase(Locale.ROOT);
  }

  private String nullableTrim(String value) {
    return value == null || value.isBlank() ? null : value.trim();
  }

  private void setStatus(MeasurementUnit unit, boolean active) {
    if (active) unit.activate();
    else unit.deactivate();
  }

  private void setStatus(SampleType sampleType, boolean active) {
    if (active) sampleType.activate();
    else sampleType.deactivate();
  }

  private void setStatus(TestDefinition test, boolean active) {
    if (active) test.activate();
    else test.deactivate();
  }

  private void setStatus(TestMethod method, boolean active) {
    if (active) method.activate();
    else method.deactivate();
  }

  private void setStatus(ResultTemplate template, boolean active) {
    if (active) template.activate();
    else template.deactivate();
  }
}
