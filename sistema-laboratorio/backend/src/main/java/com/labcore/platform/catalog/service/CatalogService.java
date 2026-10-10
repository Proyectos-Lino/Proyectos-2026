package com.labcore.platform.catalog;

import com.labcore.platform.common.exception.BusinessRuleViolationException;
import com.labcore.platform.common.exception.ResourceNotFoundException;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class CatalogService {

  private final LaboratoryAreaRepository areaRepository;
  private final SampleTypeRepository sampleTypeRepository;
  private final MeasurementUnitRepository unitRepository;
  private final TestDefinitionRepository testRepository;
  private final TestMethodRepository methodRepository;
  private final LaboratoryServiceRepository serviceRepository;
  private final ServiceTestLinkRepository serviceTestRepository;
  private final ProductRepository productRepository;
  private final ProductVarietyRepository varietyRepository;

  public CatalogService(
      LaboratoryAreaRepository areaRepository,
      SampleTypeRepository sampleTypeRepository,
      MeasurementUnitRepository unitRepository,
      TestDefinitionRepository testRepository,
      TestMethodRepository methodRepository,
      LaboratoryServiceRepository serviceRepository,
      ServiceTestLinkRepository serviceTestRepository,
      ProductRepository productRepository,
      ProductVarietyRepository varietyRepository) {
    this.areaRepository = areaRepository;
    this.sampleTypeRepository = sampleTypeRepository;
    this.unitRepository = unitRepository;
    this.testRepository = testRepository;
    this.methodRepository = methodRepository;
    this.serviceRepository = serviceRepository;
    this.serviceTestRepository = serviceTestRepository;
    this.productRepository = productRepository;
    this.varietyRepository = varietyRepository;
  }

  @Transactional(readOnly = true)
  public CatalogDtos.CatalogSnapshot getSnapshot() {
    List<CatalogDtos.Item> areas =
        areaRepository.findByActiveTrueOrderByNameAsc().stream()
            .map(area -> new CatalogDtos.Item(area.getId(), area.getCode(), area.getName()))
            .toList();
    List<CatalogDtos.SampleTypeItem> sampleTypes =
        sampleTypeRepository.findByActiveTrueOrderByNameAsc().stream()
            .map(
                type ->
                    new CatalogDtos.SampleTypeItem(
                        type.getId(),
                        type.getCode(),
                        type.getName(),
                        type.getLaboratoryCodePrefix(),
                        type.getResultMode()))
            .toList();
    List<CatalogDtos.UnitItem> units =
        unitRepository.findByActiveTrueOrderByNameAsc().stream()
            .map(
                unit ->
                    new CatalogDtos.UnitItem(
                        unit.getId(), unit.getCode(), unit.getName(), unit.getSymbol()))
            .toList();
    List<CatalogDtos.TestItem> tests =
        testRepository.findByActiveTrueOrderByNameAsc().stream()
            .map(
                test ->
                    new CatalogDtos.TestItem(
                        test.getId(),
                        test.getCode(),
                        test.getName(),
                        test.getResultType(),
                        test.getResultUnitId()))
            .toList();
    List<CatalogDtos.ServiceItem> services =
        serviceRepository.findByActiveTrueOrderByNameAsc().stream()
            .map(
                service ->
                    new CatalogDtos.ServiceItem(
                        service.getId(),
                        service.getCode(),
                        service.getName(),
                        service.getLaboratoryAreaId()))
            .toList();
    List<CatalogDtos.TestMethodItem> methods =
        methodRepository.findAll().stream()
            .filter(TestMethod::isActive)
            .map(
                method ->
                    new CatalogDtos.TestMethodItem(
                        method.getId(),
                        method.getTestDefinitionId(),
                        method.getCode(),
                        method.getName(),
                        method.getVersionLabel()))
            .toList();
    List<CatalogDtos.ServiceTestItem> serviceTests =
        serviceTestRepository.findAll().stream()
            .map(
                link ->
                    new CatalogDtos.ServiceTestItem(
                        link.getId().getServiceId(),
                        link.getId().getTestDefinitionId(),
                        link.getDisplayOrder(),
                        link.isRequired()))
            .toList();
    List<CatalogDtos.ProductItem> products =
        productRepository.findByActiveTrueOrderByNameAsc().stream()
            .map(
                product ->
                    new CatalogDtos.ProductItem(
                        product.getId(), product.getCode(), product.getName()))
            .toList();
    List<CatalogDtos.VarietyItem> varieties =
        varietyRepository.findAll().stream()
            .filter(ProductVariety::isActive)
            .map(
                variety ->
                    new CatalogDtos.VarietyItem(
                        variety.getId(),
                        variety.getProductId(),
                        variety.getCode(),
                        variety.getName()))
            .toList();
    return new CatalogDtos.CatalogSnapshot(
        areas, sampleTypes, units, tests, services, methods, serviceTests, products, varieties);
  }

  public CatalogDtos.Item createArea(CatalogDtos.AreaRequest request) {
    ensureCodeAvailable(
        areaRepository.existsByCodeIgnoreCase(normalizeCode(request.code())),
        "área",
        request.code());
    LaboratoryArea area =
        areaRepository.save(
            new LaboratoryArea(normalizeCode(request.code()), request.name().trim()));
    return new CatalogDtos.Item(area.getId(), area.getCode(), area.getName());
  }

  public CatalogDtos.UnitItem createUnit(CatalogDtos.UnitRequest request) {
    ensureCodeAvailable(
        unitRepository.existsByCodeIgnoreCase(normalizeCode(request.code())),
        "unidad",
        request.code());
    MeasurementUnit unit =
        unitRepository.save(
            new MeasurementUnit(
                normalizeCode(request.code()), request.name().trim(), request.symbol().trim()));
    return new CatalogDtos.UnitItem(unit.getId(), unit.getCode(), unit.getName(), unit.getSymbol());
  }

  public CatalogDtos.SampleTypeItem createSampleType(CatalogDtos.SampleTypeRequest request) {
    ensureCodeAvailable(
        sampleTypeRepository.existsByCodeIgnoreCase(normalizeCode(request.code())),
        "tipo de muestra",
        request.code());
    SampleType sampleType =
        sampleTypeRepository.save(
            new SampleType(
                normalizeCode(request.code()),
                request.name().trim(),
                normalizeCode(request.laboratoryCodePrefix()),
                request.resultMode()));
    return new CatalogDtos.SampleTypeItem(
        sampleType.getId(),
        sampleType.getCode(),
        sampleType.getName(),
        sampleType.getLaboratoryCodePrefix(),
        sampleType.getResultMode());
  }

  public CatalogDtos.TestItem createTest(CatalogDtos.TestRequest request) {
    ensureCodeAvailable(
        testRepository.existsByCodeIgnoreCase(normalizeCode(request.code())),
        "ensayo",
        request.code());
    if (request.resultUnitId() != null) getUnit(request.resultUnitId());
    TestDefinition test =
        testRepository.save(
            new TestDefinition(
                normalizeCode(request.code()),
                request.name().trim(),
                request.resultType(),
                request.resultUnitId()));
    return new CatalogDtos.TestItem(
        test.getId(), test.getCode(), test.getName(), test.getResultType(), test.getResultUnitId());
  }

  public CatalogDtos.Item createTestMethod(CatalogDtos.TestMethodRequest request) {
    getTest(request.testDefinitionId());
    TestMethod method =
        methodRepository.save(
            new TestMethod(
                request.testDefinitionId(),
                normalizeCode(request.code()),
                request.name().trim(),
                nullableTrim(request.versionLabel())));
    return new CatalogDtos.Item(method.getId(), method.getCode(), method.getName());
  }

  public CatalogDtos.ServiceItem createService(CatalogDtos.ServiceRequest request) {
    ensureCodeAvailable(
        serviceRepository.existsByCodeIgnoreCase(normalizeCode(request.code())),
        "servicio",
        request.code());
    getArea(request.laboratoryAreaId());
    LaboratoryService service =
        serviceRepository.save(
            new LaboratoryService(
                normalizeCode(request.code()), request.name().trim(), request.laboratoryAreaId()));
    int displayOrder = 1;
    for (UUID testId : request.testIds().stream().distinct().toList()) {
      getTest(testId);
      serviceTestRepository.save(
          new ServiceTestLink(new ServiceTestId(service.getId(), testId), displayOrder++));
    }
    return new CatalogDtos.ServiceItem(
        service.getId(), service.getCode(), service.getName(), service.getLaboratoryAreaId());
  }

  public LaboratoryArea getArea(UUID id) {
    return areaRepository
        .findById(id)
        .orElseThrow(() -> new ResourceNotFoundException("el área de laboratorio", id));
  }

  public SampleType getSampleType(UUID id) {
    return sampleTypeRepository
        .findById(id)
        .orElseThrow(() -> new ResourceNotFoundException("el tipo de muestra", id));
  }

  public MeasurementUnit getUnit(UUID id) {
    return unitRepository
        .findById(id)
        .orElseThrow(() -> new ResourceNotFoundException("la unidad de medida", id));
  }

  public LaboratoryService getService(UUID id) {
    return serviceRepository
        .findById(id)
        .orElseThrow(() -> new ResourceNotFoundException("el servicio", id));
  }

  public TestDefinition getTest(UUID id) {
    return testRepository
        .findById(id)
        .orElseThrow(() -> new ResourceNotFoundException("el ensayo", id));
  }

  public TestMethod getMethod(UUID methodId) {
    return methodRepository
        .findById(methodId)
        .orElseThrow(() -> new ResourceNotFoundException("el método de ensayo", methodId));
  }

  public TestMethod getMethod(UUID methodId, UUID testId) {
    return methodRepository
        .findByIdAndTestDefinitionIdAndActiveTrue(methodId, testId)
        .orElseThrow(
            () ->
                new BusinessRuleViolationException(
                    "El método seleccionado no corresponde al ensayo indicado."));
  }

  public boolean serviceIncludesTest(UUID serviceId, UUID testId) {
    return serviceTestRepository.existsByIdServiceIdAndIdTestDefinitionId(serviceId, testId);
  }

  private void ensureCodeAvailable(boolean exists, String resource, String code) {
    if (exists)
      throw new BusinessRuleViolationException(
          "Ya existe %s con el código %s.".formatted(resource, normalizeCode(code)));
  }

  private String normalizeCode(String value) {
    return value.trim().toUpperCase();
  }

  private String nullableTrim(String value) {
    return value == null || value.isBlank() ? null : value.trim();
  }
}
