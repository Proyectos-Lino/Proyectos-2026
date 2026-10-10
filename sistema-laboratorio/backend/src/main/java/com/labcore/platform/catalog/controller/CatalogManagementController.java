package com.labcore.platform.catalog;

import com.labcore.platform.common.exception.BusinessRuleViolationException;
import com.labcore.platform.common.i18n.SystemLabel;
import com.labcore.platform.common.web.PageResponse;
import jakarta.validation.Valid;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/catalog-management")
@PreAuthorize("hasRole('ADMIN')")
public class CatalogManagementController {

  private final TechnicalCatalogManagementService technicalCatalogService;
  private final CommercialCatalogManagementService commercialCatalogService;

  public CatalogManagementController(
      TechnicalCatalogManagementService technicalCatalogService,
      CommercialCatalogManagementService commercialCatalogService) {
    this.technicalCatalogService = technicalCatalogService;
    this.commercialCatalogService = commercialCatalogService;
  }

  @GetMapping("/units")
  public PageResponse<TechnicalCatalogDtos.UnitResponse> units(
      @RequestParam(required = false) String search,
      @RequestParam(required = false) Boolean active,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    return PageResponse.from(technicalCatalogService.findUnits(search, active, page(page, size)));
  }

  @PostMapping("/units")
  public TechnicalCatalogDtos.UnitResponse createUnit(
      @Valid @RequestBody TechnicalCatalogDtos.UnitRequest request) {
    return technicalCatalogService.createUnit(request);
  }

  @PutMapping("/units/{id}")
  public TechnicalCatalogDtos.UnitResponse updateUnit(
      @PathVariable UUID id, @Valid @RequestBody TechnicalCatalogDtos.UnitRequest request) {
    return technicalCatalogService.updateUnit(id, request);
  }

  @PatchMapping("/units/{id}/status")
  public TechnicalCatalogDtos.UnitResponse updateUnitStatus(
      @PathVariable UUID id, @Valid @RequestBody TechnicalCatalogDtos.StatusRequest request) {
    return technicalCatalogService.updateUnitStatus(id, request);
  }

  @GetMapping("/sample-types")
  public PageResponse<TechnicalCatalogDtos.SampleTypeResponse> sampleTypes(
      @RequestParam(required = false) String search,
      @RequestParam(required = false) Boolean active,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    return PageResponse.from(
        technicalCatalogService.findSampleTypes(search, active, page(page, size)));
  }

  @PostMapping("/sample-types")
  public TechnicalCatalogDtos.SampleTypeResponse createSampleType(
      @Valid @RequestBody TechnicalCatalogDtos.SampleTypeRequest request) {
    return technicalCatalogService.createSampleType(request);
  }

  @PutMapping("/sample-types/{id}")
  public TechnicalCatalogDtos.SampleTypeResponse updateSampleType(
      @PathVariable UUID id, @Valid @RequestBody TechnicalCatalogDtos.SampleTypeRequest request) {
    return technicalCatalogService.updateSampleType(id, request);
  }

  @PatchMapping("/sample-types/{id}/status")
  public TechnicalCatalogDtos.SampleTypeResponse updateSampleTypeStatus(
      @PathVariable UUID id, @Valid @RequestBody TechnicalCatalogDtos.StatusRequest request) {
    return technicalCatalogService.updateSampleTypeStatus(id, request);
  }

  @GetMapping("/tests")
  public PageResponse<TechnicalCatalogDtos.TestResponse> tests(
      @RequestParam(required = false) String search,
      @RequestParam(required = false) Boolean active,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    return PageResponse.from(technicalCatalogService.findTests(search, active, page(page, size)));
  }

  @PostMapping("/tests")
  public TechnicalCatalogDtos.TestResponse createTest(
      @Valid @RequestBody TechnicalCatalogDtos.TestRequest request) {
    return technicalCatalogService.createTest(request);
  }

  @PutMapping("/tests/{id}")
  public TechnicalCatalogDtos.TestResponse updateTest(
      @PathVariable UUID id, @Valid @RequestBody TechnicalCatalogDtos.TestRequest request) {
    return technicalCatalogService.updateTest(id, request);
  }

  @PatchMapping("/tests/{id}/status")
  public TechnicalCatalogDtos.TestResponse updateTestStatus(
      @PathVariable UUID id, @Valid @RequestBody TechnicalCatalogDtos.StatusRequest request) {
    return technicalCatalogService.updateTestStatus(id, request);
  }

  @GetMapping("/methods")
  public PageResponse<TechnicalCatalogDtos.MethodResponse> methods(
      @RequestParam(required = false) String search,
      @RequestParam(required = false) Boolean active,
      @RequestParam(required = false) UUID testDefinitionId,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    return PageResponse.from(
        technicalCatalogService.findMethods(search, active, testDefinitionId, page(page, size)));
  }

  @PostMapping("/methods")
  public TechnicalCatalogDtos.MethodResponse createMethod(
      @Valid @RequestBody TechnicalCatalogDtos.MethodRequest request) {
    return technicalCatalogService.createMethod(request);
  }

  @PutMapping("/methods/{id}")
  public TechnicalCatalogDtos.MethodResponse updateMethod(
      @PathVariable UUID id, @Valid @RequestBody TechnicalCatalogDtos.MethodRequest request) {
    return technicalCatalogService.updateMethod(id, request);
  }

  @PatchMapping("/methods/{id}/status")
  public TechnicalCatalogDtos.MethodResponse updateMethodStatus(
      @PathVariable UUID id, @Valid @RequestBody TechnicalCatalogDtos.StatusRequest request) {
    return technicalCatalogService.updateMethodStatus(id, request);
  }

  @GetMapping("/result-templates")
  public PageResponse<TechnicalCatalogDtos.TemplateResponse> templates(
      @RequestParam(required = false) String search,
      @RequestParam(required = false) Boolean active,
      @RequestParam(required = false) UUID testDefinitionId,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    return PageResponse.from(
        technicalCatalogService.findTemplates(search, active, testDefinitionId, page(page, size)));
  }

  @PostMapping("/result-templates")
  public TechnicalCatalogDtos.TemplateResponse createTemplate(
      @Valid @RequestBody TechnicalCatalogDtos.TemplateRequest request) {
    return technicalCatalogService.createTemplate(request);
  }

  @PostMapping("/result-templates/formula-preview")
  public TechnicalCatalogDtos.FormulaPreviewResponse previewFormula(
      @Valid @RequestBody TechnicalCatalogDtos.FormulaPreviewRequest request) {
    return technicalCatalogService.previewFormula(request);
  }

  @PutMapping("/result-templates/{id}")
  public TechnicalCatalogDtos.TemplateResponse updateTemplate(
      @PathVariable UUID id, @Valid @RequestBody TechnicalCatalogDtos.TemplateRequest request) {
    return technicalCatalogService.updateTemplate(id, request);
  }

  @PatchMapping("/result-templates/{id}/status")
  public TechnicalCatalogDtos.TemplateResponse updateTemplateStatus(
      @PathVariable UUID id, @Valid @RequestBody TechnicalCatalogDtos.StatusRequest request) {
    return technicalCatalogService.updateTemplateStatus(id, request);
  }

  @GetMapping("/services")
  public PageResponse<CommercialCatalogDtos.ServiceResponse> services(
      @RequestParam(required = false) String search,
      @RequestParam(required = false) Boolean active,
      @RequestParam(required = false) UUID laboratoryAreaId,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    return PageResponse.from(
        commercialCatalogService.findServices(search, active, laboratoryAreaId, page(page, size)));
  }

  @PostMapping("/services")
  public CommercialCatalogDtos.ServiceResponse createService(
      @Valid @RequestBody CommercialCatalogDtos.ServiceRequest request) {
    return commercialCatalogService.createService(request);
  }

  @PutMapping("/services/{id}")
  public CommercialCatalogDtos.ServiceResponse updateService(
      @PathVariable UUID id, @Valid @RequestBody CommercialCatalogDtos.ServiceRequest request) {
    return commercialCatalogService.updateService(id, request);
  }

  @PatchMapping("/services/{id}/status")
  public CommercialCatalogDtos.ServiceResponse updateServiceStatus(
      @PathVariable UUID id, @Valid @RequestBody CommercialCatalogDtos.StatusRequest request) {
    return commercialCatalogService.updateServiceStatus(id, request);
  }

  @GetMapping("/products")
  public PageResponse<CommercialCatalogDtos.ProductResponse> products(
      @RequestParam(required = false) String search,
      @RequestParam(required = false) Boolean active,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    return PageResponse.from(
        commercialCatalogService.findProducts(search, active, page(page, size)));
  }

  @PostMapping("/products")
  public CommercialCatalogDtos.ProductResponse createProduct(
      @Valid @RequestBody CommercialCatalogDtos.ProductRequest request) {
    return commercialCatalogService.createProduct(request);
  }

  @PutMapping("/products/{id}")
  public CommercialCatalogDtos.ProductResponse updateProduct(
      @PathVariable UUID id, @Valid @RequestBody CommercialCatalogDtos.ProductRequest request) {
    return commercialCatalogService.updateProduct(id, request);
  }

  @PatchMapping("/products/{id}/status")
  public CommercialCatalogDtos.ProductResponse updateProductStatus(
      @PathVariable UUID id, @Valid @RequestBody CommercialCatalogDtos.StatusRequest request) {
    return commercialCatalogService.updateProductStatus(id, request);
  }

  @GetMapping("/varieties")
  public PageResponse<CommercialCatalogDtos.VarietyResponse> varieties(
      @RequestParam(required = false) String search,
      @RequestParam(required = false) Boolean active,
      @RequestParam(required = false) UUID productId,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    return PageResponse.from(
        commercialCatalogService.findVarieties(search, active, productId, page(page, size)));
  }

  @PostMapping("/varieties")
  public CommercialCatalogDtos.VarietyResponse createVariety(
      @Valid @RequestBody CommercialCatalogDtos.VarietyRequest request) {
    return commercialCatalogService.createVariety(request);
  }

  @PutMapping("/varieties/{id}")
  public CommercialCatalogDtos.VarietyResponse updateVariety(
      @PathVariable UUID id, @Valid @RequestBody CommercialCatalogDtos.VarietyRequest request) {
    return commercialCatalogService.updateVariety(id, request);
  }

  @PatchMapping("/varieties/{id}/status")
  public CommercialCatalogDtos.VarietyResponse updateVarietyStatus(
      @PathVariable UUID id, @Valid @RequestBody CommercialCatalogDtos.StatusRequest request) {
    return commercialCatalogService.updateVarietyStatus(id, request);
  }

  @GetMapping(value = "/export", produces = "text/csv")
  public ResponseEntity<byte[]> export(@RequestParam String type) {
    String csv =
        switch (type) {
          case "units" -> unitsCsv();
          case "sample-types" -> sampleTypesCsv();
          case "tests" -> testsCsv();
          case "methods" -> methodsCsv();
          case "result-templates" -> templatesCsv();
          case "services" -> servicesCsv();
          case "products" -> productsCsv();
          case "varieties" -> varietiesCsv();
          default ->
              throw new BusinessRuleViolationException(
                  "No existe un catálogo exportable con ese tipo.");
        };
    return ResponseEntity.ok()
        .header(
            HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"catalogo-" + type + ".csv\"")
        .contentType(MediaType.parseMediaType("text/csv;charset=UTF-8"))
        .body(csv.getBytes(StandardCharsets.UTF_8));
  }

  private Pageable page(int page, int size) {
    return PageRequest.of(
        Math.max(0, page), Math.min(Math.max(1, size), 100), Sort.by(Sort.Direction.ASC, "name"));
  }

  private Pageable exportPage() {
    return PageRequest.of(0, 10_000, Sort.by(Sort.Direction.ASC, "name"));
  }

  private String unitsCsv() {
    return CatalogCsvExporter.render(
        List.of("Código", "Nombre", "Símbolo", "Estado"),
        technicalCatalogService.findUnits(null, null, exportPage()).stream()
            .map(unit -> List.of(unit.code(), unit.name(), unit.symbol(), state(unit.active())))
            .toList());
  }

  private String sampleTypesCsv() {
    return CatalogCsvExporter.render(
        List.of("Código", "Nombre", "Prefijo", "Modo", "Estado"),
        technicalCatalogService.findSampleTypes(null, null, exportPage()).stream()
            .map(
                type ->
                    List.of(
                        type.code(),
                        type.name(),
                        type.laboratoryCodePrefix(),
                        SystemLabel.forValue(type.resultMode()),
                        state(type.active())))
            .toList());
  }

  private String testsCsv() {
    return CatalogCsvExporter.render(
        List.of("Código", "Nombre", "Tipo de resultado", "Estado"),
        technicalCatalogService.findTests(null, null, exportPage()).stream()
            .map(
                test ->
                    List.of(
                        test.code(),
                        test.name(),
                        SystemLabel.forValue(test.resultType()),
                        state(test.active())))
            .toList());
  }

  private String methodsCsv() {
    return CatalogCsvExporter.render(
        List.of("Código", "Nombre", "Versión", "Estado"),
        technicalCatalogService.findMethods(null, null, null, exportPage()).stream()
            .map(
                method ->
                    List.of(
                        method.code(),
                        method.name(),
                        method.versionLabel(),
                        state(method.active())))
            .toList());
  }

  private String templatesCsv() {
    return CatalogCsvExporter.render(
        List.of("Plantilla", "Ensayo", "Campos", "Estado"),
        technicalCatalogService.findTemplates(null, null, null, exportPage()).stream()
            .map(
                template ->
                    List.of(
                        template.name(),
                        template.testDefinitionName(),
                        String.valueOf(template.fields().size()),
                        state(template.active())))
            .toList());
  }

  private String servicesCsv() {
    return CatalogCsvExporter.render(
        List.of("Código", "Servicio", "Área", "Ensayos", "Estado"),
        commercialCatalogService.findServices(null, null, null, exportPage()).stream()
            .map(
                service ->
                    List.of(
                        service.code(),
                        service.name(),
                        service.laboratoryAreaName(),
                        String.valueOf(service.tests().size()),
                        state(service.active())))
            .toList());
  }

  private String productsCsv() {
    return CatalogCsvExporter.render(
        List.of("Código", "Producto", "Estado"),
        commercialCatalogService.findProducts(null, null, exportPage()).stream()
            .map(product -> List.of(product.code(), product.name(), state(product.active())))
            .toList());
  }

  private String varietiesCsv() {
    return CatalogCsvExporter.render(
        List.of("Código", "Variedad", "Producto", "Estado"),
        commercialCatalogService.findVarieties(null, null, null, exportPage()).stream()
            .map(
                variety ->
                    List.of(
                        variety.code(),
                        variety.name(),
                        variety.productName(),
                        state(variety.active())))
            .toList());
  }

  private String state(boolean active) {
    return active ? "Activo" : "Inactivo";
  }
}
