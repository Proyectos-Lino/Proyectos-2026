package com.labcore.platform.catalog;

import com.labcore.platform.common.exception.BusinessRuleViolationException;
import com.labcore.platform.common.exception.ResourceNotFoundException;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class CommercialCatalogManagementService {

  private final LaboratoryServiceRepository serviceRepository;
  private final ServiceTestLinkRepository serviceTestRepository;
  private final LaboratoryAreaRepository areaRepository;
  private final TestDefinitionRepository testRepository;
  private final ProductRepository productRepository;
  private final ProductVarietyRepository varietyRepository;

  public CommercialCatalogManagementService(
      LaboratoryServiceRepository serviceRepository,
      ServiceTestLinkRepository serviceTestRepository,
      LaboratoryAreaRepository areaRepository,
      TestDefinitionRepository testRepository,
      ProductRepository productRepository,
      ProductVarietyRepository varietyRepository) {
    this.serviceRepository = serviceRepository;
    this.serviceTestRepository = serviceTestRepository;
    this.areaRepository = areaRepository;
    this.testRepository = testRepository;
    this.productRepository = productRepository;
    this.varietyRepository = varietyRepository;
  }

  public CommercialCatalogDtos.ServiceResponse createService(
      CommercialCatalogDtos.ServiceRequest request) {
    String code = normalizeCode(request.code());
    if (serviceRepository.existsByCodeIgnoreCase(code)) {
      throw duplicateCode("servicio", code);
    }
    requireActiveArea(request.laboratoryAreaId());
    validateServiceTests(request.tests());
    LaboratoryService service =
        serviceRepository.save(
            new LaboratoryService(code, request.name().trim(), request.laboratoryAreaId()));
    replaceServiceTests(service.getId(), request.tests());
    return toService(service);
  }

  public CommercialCatalogDtos.ServiceResponse updateService(
      UUID id, CommercialCatalogDtos.ServiceRequest request) {
    LaboratoryService service = requiredService(id);
    String code = normalizeCode(request.code());
    if (serviceRepository.existsByCodeIgnoreCaseAndIdNot(code, id)) {
      throw duplicateCode("servicio", code);
    }
    requireActiveArea(request.laboratoryAreaId());
    validateServiceTests(request.tests());
    service.update(code, request.name().trim(), request.laboratoryAreaId());
    serviceTestRepository.deleteByIdServiceId(id);
    replaceServiceTests(id, request.tests());
    return toService(service);
  }

  public CommercialCatalogDtos.ServiceResponse updateServiceStatus(
      UUID id, CommercialCatalogDtos.StatusRequest request) {
    LaboratoryService service = requiredService(id);
    if (request.active()) service.activate();
    else service.deactivate();
    return toService(service);
  }

  @Transactional(readOnly = true)
  public Page<CommercialCatalogDtos.ServiceResponse> findServices(
      String search, Boolean active, UUID laboratoryAreaId, Pageable pageable) {
    Specification<LaboratoryService> specification =
        CatalogSpecifications.<LaboratoryService>textSearch(search, "code", "name")
            .and(CatalogSpecifications.active(active));
    if (laboratoryAreaId != null) {
      specification =
          specification.and(CatalogSpecifications.equalTo("laboratoryAreaId", laboratoryAreaId));
    }
    return serviceRepository.findAll(specification, pageable).map(this::toService);
  }

  public CommercialCatalogDtos.ProductResponse createProduct(
      CommercialCatalogDtos.ProductRequest request) {
    String code = normalizeCode(request.code());
    if (productRepository.existsByCodeIgnoreCase(code)) {
      throw duplicateCode("producto", code);
    }
    return toProduct(productRepository.save(new Product(code, request.name().trim())));
  }

  public CommercialCatalogDtos.ProductResponse updateProduct(
      UUID id, CommercialCatalogDtos.ProductRequest request) {
    Product product = requiredProduct(id);
    String code = normalizeCode(request.code());
    if (productRepository.existsByCodeIgnoreCaseAndIdNot(code, id)) {
      throw duplicateCode("producto", code);
    }
    product.update(code, request.name().trim());
    return toProduct(product);
  }

  public CommercialCatalogDtos.ProductResponse updateProductStatus(
      UUID id, CommercialCatalogDtos.StatusRequest request) {
    Product product = requiredProduct(id);
    if (request.active()) product.activate();
    else product.deactivate();
    return toProduct(product);
  }

  @Transactional(readOnly = true)
  public Page<CommercialCatalogDtos.ProductResponse> findProducts(
      String search, Boolean active, Pageable pageable) {
    return productRepository
        .findAll(
            CatalogSpecifications.<Product>textSearch(search, "code", "name")
                .and(CatalogSpecifications.active(active)),
            pageable)
        .map(this::toProduct);
  }

  public CommercialCatalogDtos.VarietyResponse createVariety(
      CommercialCatalogDtos.VarietyRequest request) {
    Product product = requireActiveProduct(request.productId());
    String code = normalizeCode(request.code());
    if (varietyRepository.existsByProductIdAndCodeIgnoreCase(product.getId(), code)) {
      throw new BusinessRuleViolationException(
          "Ya existe una variedad con ese código para el producto.");
    }
    return toVariety(
        varietyRepository.save(new ProductVariety(product.getId(), code, request.name().trim())));
  }

  public CommercialCatalogDtos.VarietyResponse updateVariety(
      UUID id, CommercialCatalogDtos.VarietyRequest request) {
    ProductVariety variety = requiredVariety(id);
    if (!variety.getProductId().equals(request.productId())) {
      throw new BusinessRuleViolationException(
          "No es posible cambiar el producto asociado a una variedad.");
    }
    String code = normalizeCode(request.code());
    if (varietyRepository.existsByProductIdAndCodeIgnoreCaseAndIdNot(
        variety.getProductId(), code, id)) {
      throw new BusinessRuleViolationException(
          "Ya existe una variedad con ese código para el producto.");
    }
    variety.update(code, request.name().trim());
    return toVariety(variety);
  }

  public CommercialCatalogDtos.VarietyResponse updateVarietyStatus(
      UUID id, CommercialCatalogDtos.StatusRequest request) {
    ProductVariety variety = requiredVariety(id);
    if (request.active()) variety.activate();
    else variety.deactivate();
    return toVariety(variety);
  }

  @Transactional(readOnly = true)
  public Page<CommercialCatalogDtos.VarietyResponse> findVarieties(
      String search, Boolean active, UUID productId, Pageable pageable) {
    Specification<ProductVariety> specification =
        CatalogSpecifications.<ProductVariety>textSearch(search, "code", "name")
            .and(CatalogSpecifications.active(active));
    if (productId != null) {
      specification = specification.and(CatalogSpecifications.equalTo("productId", productId));
    }
    return varietyRepository.findAll(specification, pageable).map(this::toVariety);
  }

  private void replaceServiceTests(
      UUID serviceId, java.util.List<CommercialCatalogDtos.ServiceTestRequest> requestedTests) {
    for (int index = 0; index < requestedTests.size(); index++) {
      CommercialCatalogDtos.ServiceTestRequest requestedTest = requestedTests.get(index);
      ServiceTestLink link =
          new ServiceTestLink(
              new ServiceTestId(serviceId, requestedTest.testDefinitionId()), index + 1);
      if (!requestedTest.required()) {
        link.markOptional();
      }
      serviceTestRepository.save(link);
    }
  }

  private void validateServiceTests(
      java.util.List<CommercialCatalogDtos.ServiceTestRequest> requestedTests) {
    Set<UUID> testIds = new HashSet<>();
    for (CommercialCatalogDtos.ServiceTestRequest requestedTest : requestedTests) {
      if (!testIds.add(requestedTest.testDefinitionId())) {
        throw new BusinessRuleViolationException(
            "Un servicio no puede incluir el mismo ensayo más de una vez.");
      }
      TestDefinition test = requiredTest(requestedTest.testDefinitionId());
      if (!test.isActive()) {
        throw new BusinessRuleViolationException(
            "No es posible agregar un ensayo inactivo a un servicio.");
      }
    }
  }

  private LaboratoryService requiredService(UUID id) {
    return serviceRepository
        .findById(id)
        .orElseThrow(() -> new ResourceNotFoundException("el servicio", id));
  }

  private LaboratoryArea requireActiveArea(UUID id) {
    LaboratoryArea area =
        areaRepository
            .findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("el área de laboratorio", id));
    if (!area.isActive()) {
      throw new BusinessRuleViolationException(
          "El área de laboratorio seleccionada se encuentra inactiva.");
    }
    return area;
  }

  private TestDefinition requiredTest(UUID id) {
    return testRepository
        .findById(id)
        .orElseThrow(() -> new ResourceNotFoundException("el ensayo", id));
  }

  private Product requiredProduct(UUID id) {
    return productRepository
        .findById(id)
        .orElseThrow(() -> new ResourceNotFoundException("el producto", id));
  }

  private Product requireActiveProduct(UUID id) {
    Product product = requiredProduct(id);
    if (!product.isActive()) {
      throw new BusinessRuleViolationException("El producto seleccionado se encuentra inactivo.");
    }
    return product;
  }

  private ProductVariety requiredVariety(UUID id) {
    return varietyRepository
        .findById(id)
        .orElseThrow(() -> new ResourceNotFoundException("la variedad", id));
  }

  private CommercialCatalogDtos.ServiceResponse toService(LaboratoryService service) {
    LaboratoryArea area =
        areaRepository
            .findById(service.getLaboratoryAreaId())
            .orElseThrow(
                () ->
                    new ResourceNotFoundException(
                        "el área de laboratorio", service.getLaboratoryAreaId()));
    return new CommercialCatalogDtos.ServiceResponse(
        service.getId(),
        service.getCode(),
        service.getName(),
        service.getLaboratoryAreaId(),
        area.getName(),
        service.isActive(),
        service.getCreatedAt(),
        serviceTestRepository.findByIdServiceIdOrderByDisplayOrderAsc(service.getId()).stream()
            .map(
                link -> {
                  TestDefinition test = requiredTest(link.getId().getTestDefinitionId());
                  return new CommercialCatalogDtos.ServiceTestResponse(
                      test.getId(), test.getName(), link.getDisplayOrder(), link.isRequired());
                })
            .toList());
  }

  private CommercialCatalogDtos.ProductResponse toProduct(Product product) {
    return new CommercialCatalogDtos.ProductResponse(
        product.getId(),
        product.getCode(),
        product.getName(),
        product.isActive(),
        product.getCreatedAt());
  }

  private CommercialCatalogDtos.VarietyResponse toVariety(ProductVariety variety) {
    Product product = requiredProduct(variety.getProductId());
    return new CommercialCatalogDtos.VarietyResponse(
        variety.getId(),
        variety.getProductId(),
        product.getName(),
        variety.getCode(),
        variety.getName(),
        variety.isActive(),
        variety.getCreatedAt());
  }

  private BusinessRuleViolationException duplicateCode(String resource, String code) {
    return new BusinessRuleViolationException(
        "Ya existe %s con el código %s.".formatted(resource, code));
  }

  private String normalizeCode(String value) {
    return value.trim().toUpperCase(Locale.ROOT);
  }
}
