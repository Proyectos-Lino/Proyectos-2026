package com.labcore.platform.catalog;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.labcore.platform.common.exception.BusinessRuleViolationException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CatalogManagementServiceTest {

  @Mock private MeasurementUnitRepository unitRepository;
  @Mock private SampleTypeRepository sampleTypeRepository;
  @Mock private TestDefinitionRepository testRepository;
  @Mock private TestMethodRepository methodRepository;
  @Mock private ResultTemplateRepository templateRepository;
  @Mock private ResultTemplateFieldRepository templateFieldRepository;
  @Mock private ResultTemplateCategoryRepository templateCategoryRepository;
  @Mock private LaboratoryServiceRepository serviceRepository;
  @Mock private ServiceTestLinkRepository serviceTestRepository;
  @Mock private LaboratoryAreaRepository areaRepository;
  @Mock private ProductRepository productRepository;
  @Mock private ProductVarietyRepository varietyRepository;
  @Mock private com.labcore.platform.formula.FormulaEngine formulaEngine;

  @Test
  void rejectsDuplicateFieldKeysInAResultTemplate() {
    UUID testId = UUID.randomUUID();
    given(testRepository.findById(testId))
        .willReturn(Optional.of(new TestDefinition("PH", "pH", ResultDataType.NUMERIC, null)));

    TechnicalCatalogDtos.TemplateFieldRequest field =
        new TechnicalCatalogDtos.TemplateFieldRequest(
            "resultado",
            "Resultado",
            ResultDataType.NUMERIC,
            null,
            true,
            1,
            ResultFieldKind.CAPTURED,
            false,
            null,
            CalculationScope.PER_REPETITION,
            null,
            null,
            null);
    TechnicalCatalogDtos.TemplateRequest request =
        new TechnicalCatalogDtos.TemplateRequest(
            testId,
            null,
            "Plantilla base",
            null,
            ResultMode.SCALAR,
            1,
            null,
            1,
            null,
            1,
            null,
            List.of(field, field),
            List.of());

    assertThatThrownBy(() -> technicalService().createTemplate(request))
        .isInstanceOf(BusinessRuleViolationException.class)
        .hasMessage("Cada campo de la plantilla debe tener una clave única.");

    verify(templateRepository, never()).save(any());
  }

  @Test
  void rejectsCalculatedFieldsInSpecializedMatrixTemplates() {
    UUID testId = UUID.randomUUID();
    given(testRepository.findById(testId))
        .willReturn(
            Optional.of(new TestDefinition("YEMAS", "Conteo", ResultDataType.NUMERIC, null)));
    TechnicalCatalogDtos.TemplateFieldRequest calculated =
        new TechnicalCatalogDtos.TemplateFieldRequest(
            "total",
            "Total",
            ResultDataType.NUMERIC,
            null,
            true,
            1,
            ResultFieldKind.CALCULATED,
            true,
            "SUM(lectura)",
            CalculationScope.AGGREGATE,
            0,
            null,
            null);
    TechnicalCatalogDtos.TemplateRequest request =
        new TechnicalCatalogDtos.TemplateRequest(
            testId,
            null,
            "Matriz",
            null,
            ResultMode.MATRIX,
            1,
            null,
            1,
            10,
            1,
            10,
            List.of(calculated),
            List.of());

    assertThatThrownBy(() -> technicalService().createTemplate(request))
        .isInstanceOf(BusinessRuleViolationException.class)
        .hasMessage(
            "Los campos calculados se admiten en resultados escalares o repetibles, no en matrices.");

    verify(templateRepository, never()).save(any());
  }

  @Test
  void rejectsRepeatedTestsInAServiceConfiguration() {
    UUID areaId = UUID.randomUUID();
    UUID testId = UUID.randomUUID();
    given(areaRepository.findById(areaId))
        .willReturn(Optional.of(new LaboratoryArea("MICRO", "Microbiología")));
    given(testRepository.findById(testId))
        .willReturn(Optional.of(new TestDefinition("PH", "pH", ResultDataType.NUMERIC, null)));

    CommercialCatalogDtos.ServiceTestRequest test =
        new CommercialCatalogDtos.ServiceTestRequest(testId, true);
    CommercialCatalogDtos.ServiceRequest request =
        new CommercialCatalogDtos.ServiceRequest(
            "SVC-PH", "Análisis de pH", areaId, List.of(test, test));

    assertThatThrownBy(() -> commercialService().createService(request))
        .isInstanceOf(BusinessRuleViolationException.class)
        .hasMessage("Un servicio no puede incluir el mismo ensayo más de una vez.");

    verify(serviceRepository, never()).save(any());
  }

  private TechnicalCatalogManagementService technicalService() {
    return new TechnicalCatalogManagementService(
        unitRepository,
        sampleTypeRepository,
        testRepository,
        methodRepository,
        templateRepository,
        templateFieldRepository,
        templateCategoryRepository,
        formulaEngine);
  }

  private CommercialCatalogManagementService commercialService() {
    return new CommercialCatalogManagementService(
        serviceRepository,
        serviceTestRepository,
        areaRepository,
        testRepository,
        productRepository,
        varietyRepository);
  }
}
