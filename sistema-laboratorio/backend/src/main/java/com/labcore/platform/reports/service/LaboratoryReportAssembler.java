package com.labcore.platform.reports;

import com.labcore.platform.catalog.CatalogService;
import com.labcore.platform.catalog.TechnicalCatalogManagementService;
import com.labcore.platform.common.exception.BusinessRuleViolationException;
import com.labcore.platform.orders.RequestedTestStatus;
import com.labcore.platform.orders.ServiceOrderDtos;
import com.labcore.platform.orders.ServiceOrderSampleTest;
import com.labcore.platform.orders.ServiceOrderSampleTestRepository;
import com.labcore.platform.orders.ServiceOrderService;
import com.labcore.platform.organization.OrganizationProfileService;
import com.labcore.platform.results.ResultEntry;
import com.labcore.platform.results.ResultEntryRepository;
import com.labcore.platform.results.ResultEntryStatus;
import com.labcore.platform.results.ResultMatrix;
import com.labcore.platform.results.ResultMatrixCellRepository;
import com.labcore.platform.results.ResultMatrixColumnRepository;
import com.labcore.platform.results.ResultMatrixRepository;
import com.labcore.platform.results.ResultValidationRepository;
import com.labcore.platform.results.ResultValueRepository;
import com.labcore.platform.security.ApplicationUserRepository;
import com.labcore.platform.workorders.WorkOrder;
import com.labcore.platform.workorders.WorkOrderRepository;
import com.labcore.platform.workorders.WorkOrderSample;
import com.labcore.platform.workorders.WorkOrderSampleRepository;
import com.labcore.platform.workorders.WorkOrderTest;
import com.labcore.platform.workorders.WorkOrderTestRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

@Component
public class LaboratoryReportAssembler {
  private final ServiceOrderService serviceOrderService;
  private final ServiceOrderSampleTestRepository sourceTestRepository;
  private final WorkOrderRepository workOrderRepository;
  private final WorkOrderSampleRepository workOrderSampleRepository;
  private final WorkOrderTestRepository workOrderTestRepository;
  private final ResultEntryRepository resultEntryRepository;
  private final ResultValueRepository resultValueRepository;
  private final ResultValidationRepository validationRepository;
  private final ResultMatrixRepository matrixRepository;
  private final ResultMatrixColumnRepository matrixColumnRepository;
  private final ResultMatrixCellRepository matrixCellRepository;
  private final ApplicationUserRepository userRepository;
  private final CatalogService catalogService;
  private final TechnicalCatalogManagementService technicalCatalogService;
  private final OrganizationProfileService organizationService;

  public LaboratoryReportAssembler(
      ServiceOrderService serviceOrderService,
      ServiceOrderSampleTestRepository sourceTestRepository,
      WorkOrderRepository workOrderRepository,
      WorkOrderSampleRepository workOrderSampleRepository,
      WorkOrderTestRepository workOrderTestRepository,
      ResultEntryRepository resultEntryRepository,
      ResultValueRepository resultValueRepository,
      ResultValidationRepository validationRepository,
      ResultMatrixRepository matrixRepository,
      ResultMatrixColumnRepository matrixColumnRepository,
      ResultMatrixCellRepository matrixCellRepository,
      ApplicationUserRepository userRepository,
      CatalogService catalogService,
      TechnicalCatalogManagementService technicalCatalogService,
      OrganizationProfileService organizationService) {
    this.serviceOrderService = serviceOrderService;
    this.sourceTestRepository = sourceTestRepository;
    this.workOrderRepository = workOrderRepository;
    this.workOrderSampleRepository = workOrderSampleRepository;
    this.workOrderTestRepository = workOrderTestRepository;
    this.resultEntryRepository = resultEntryRepository;
    this.resultValueRepository = resultValueRepository;
    this.validationRepository = validationRepository;
    this.matrixRepository = matrixRepository;
    this.matrixColumnRepository = matrixColumnRepository;
    this.matrixCellRepository = matrixCellRepository;
    this.userRepository = userRepository;
    this.catalogService = catalogService;
    this.technicalCatalogService = technicalCatalogService;
    this.organizationService = organizationService;
  }

  public LaboratoryReportDtos.Snapshot assemble(
      UUID serviceOrderId, String reportCode, int version) {
    ServiceOrderDtos.ServiceOrderDetail order = serviceOrderService.getDetail(serviceOrderId);
    List<WorkOrder> workOrders =
        workOrderRepository.findByServiceOrderIdOrderByCreatedAtAsc(serviceOrderId);
    List<WorkOrderSample> technicalSamples =
        workOrders.stream()
            .flatMap(
                workOrder ->
                    workOrderSampleRepository.findByWorkOrderId(workOrder.getId()).stream())
            .toList();
    List<WorkOrderTest> technicalTests =
        technicalSamples.isEmpty()
            ? List.of()
            : workOrderTestRepository.findByWorkOrderSampleIdInOrderByCreatedAtAsc(
                technicalSamples.stream().map(WorkOrderSample::getId).toList());
    Map<UUID, WorkOrderSample> sampleBySourceId =
        technicalSamples.stream()
            .collect(
                Collectors.toMap(
                    WorkOrderSample::getServiceOrderSampleId,
                    Function.identity(),
                    (first, ignored) -> first));
    Map<UUID, WorkOrderTest> testBySourceId =
        technicalTests.stream()
            .collect(
                Collectors.toMap(
                    WorkOrderTest::getSourceSampleTestId,
                    Function.identity(),
                    (first, ignored) -> first));

    List<LaboratoryReportDtos.SampleResult> samples =
        order.samples().stream()
            .map(sample -> toSample(sample, sampleBySourceId.get(sample.id()), testBySourceId))
            .toList();
    return new LaboratoryReportDtos.Snapshot(
        reportCode,
        version,
        Instant.now(),
        organizationService.documentIdentity(),
        order.code(),
        order.clientName(),
        order.clientDocument(),
        order.reportName(),
        order.reportEmail(),
        order.reportAddress(),
        order.laboratoryAreaName(),
        order.generalObservations(),
        samples);
  }

  private LaboratoryReportDtos.SampleResult toSample(
      ServiceOrderDtos.SampleResponse sample,
      WorkOrderSample technicalSample,
      Map<UUID, WorkOrderTest> testBySourceId) {
    if (technicalSample == null) {
      throw new BusinessRuleViolationException(
          "La muestra aún no cuenta con trazabilidad técnica.");
    }
    Map<UUID, ServiceOrderSampleTest> sourceTestById =
        sourceTestRepository
            .findAllById(
                sample.requestedTests().stream()
                    .map(ServiceOrderDtos.RequestedTestResponse::id)
                    .toList())
            .stream()
            .collect(Collectors.toMap(ServiceOrderSampleTest::getId, Function.identity()));
    List<ServiceOrderSampleTest> sourceTests =
        sample.requestedTests().stream()
            .map(ServiceOrderDtos.RequestedTestResponse::id)
            .map(sourceTestById::get)
            .toList();
    if (sourceTests.stream().anyMatch(java.util.Objects::isNull)) {
      throw new BusinessRuleViolationException(
          "No fue posible recuperar todos los ensayos solicitados para la muestra.");
    }
    if (sourceTests.stream().anyMatch(test -> test.getStatus() != RequestedTestStatus.COMPLETED)) {
      throw new BusinessRuleViolationException(
          "Existen ensayos que todavía no han sido completados.");
    }
    List<LaboratoryReportDtos.TestResult> tests =
        sourceTests.stream().map(test -> toTest(test, testBySourceId.get(test.getId()))).toList();
    return new LaboratoryReportDtos.SampleResult(
        sample.commercialSequence(),
        technicalSample.getLaboratoryCode(),
        sample.clientIdentification(),
        sample.producerName(),
        sample.producerDocument(),
        sample.productName(),
        sample.varietyName(),
        catalogService.getSampleType(sample.sampleTypeId()).getName(),
        sample.physicalDescription(),
        sample.origin(),
        sample.samplingLocation(),
        sample.sampledAt(),
        sample.quantity(),
        sample.measurementUnitId() == null
            ? null
            : catalogService.getUnit(sample.measurementUnitId()).getSymbol(),
        sample.containerDescription(),
        sample.batchNumber(),
        sample.brand(),
        sample.observations(),
        tests);
  }

  private LaboratoryReportDtos.TestResult toTest(
      ServiceOrderSampleTest sourceTest, WorkOrderTest technicalTest) {
    if (technicalTest == null) {
      throw new BusinessRuleViolationException("El ensayo no cuenta con una asignación técnica.");
    }
    ResultEntry result =
        resultEntryRepository
            .findByWorkOrderTestId(technicalTest.getId())
            .filter(entry -> entry.getStatus() == ResultEntryStatus.VALIDATED)
            .orElseThrow(
                () ->
                    new BusinessRuleViolationException(
                        "Todos los resultados deben estar validados."));
    var validation =
        validationRepository
            .findByResultEntryId(result.getId())
            .orElseThrow(
                () -> new BusinessRuleViolationException("Falta la validación del resultado."));
    if (validation.getResultRevision() != result.getResultRevision()) {
      throw new BusinessRuleViolationException(
          "La versión vigente del resultado no coincide con la versión validada.");
    }
    List<LaboratoryReportDtos.Value> values =
        resultValueRepository.findByResultEntryIdOrderByPositionAsc(result.getId()).stream()
            .map(
                value ->
                    new LaboratoryReportDtos.Value(
                        value.getLabel(),
                        value.getRepetitionIndex() + 1,
                        value.getValueText(),
                        value.getValueNumber(),
                        value.getMeasurementUnitId() == null
                            ? null
                            : catalogService.getUnit(value.getMeasurementUnitId()).getSymbol(),
                        value.getFieldKind(),
                        value.getFormulaUsed(),
                        value.getCalculationInputs()))
            .toList();
    LaboratoryReportDtos.Matrix matrix = matrix(result);
    return new LaboratoryReportDtos.TestResult(
        catalogService.getTest(sourceTest.getTestDefinitionId()).getName(),
        catalogService.getMethod(technicalTest.getTestMethodId()).getName(),
        userRepository
            .findById(result.getRecordedBy())
            .map(user -> user.getFullName())
            .orElse("Usuario no disponible"),
        result.getAnalystObservation(),
        userRepository
            .findById(validation.getValidatorId())
            .map(user -> user.getFullName())
            .orElse("Usuario no disponible"),
        validation.getValidatedAt(),
        values,
        matrix);
  }

  private LaboratoryReportDtos.Matrix matrix(ResultEntry result) {
    ResultMatrix matrix = matrixRepository.findByResultEntryId(result.getId()).orElse(null);
    if (matrix == null) {
      return null;
    }
    Map<String, String> categoryLabels = categoryLabels(result.getResultTemplateId());
    List<LaboratoryReportDtos.MatrixCell> cells =
        matrixCellRepository
            .findByResultMatrixIdOrderByRowIndexAscColumnIndexAsc(matrix.getId())
            .stream()
            .map(
                cell ->
                    new LaboratoryReportDtos.MatrixCell(
                        cell.getRowIndex(),
                        cell.getColumnIndex(),
                        cell.getValueText(),
                        cell.getValueNumber(),
                        categoryLabel(cell.getCategory(), categoryLabels)))
            .toList();
    return new LaboratoryReportDtos.Matrix(
        matrix.getRowCount(),
        matrix.getColumnCount(),
        matrixColumnRepository.findByResultMatrixIdOrderByColumnIndexAsc(matrix.getId()).stream()
            .map(
                column ->
                    new LaboratoryReportDtos.MatrixColumn(
                        column.getColumnIndex(),
                        column.getCarrierLabel(),
                        column.getDistance(),
                        column.getDiameter(),
                        column.getShape()))
            .toList(),
        cells,
        matrixSummary(cells, matrix.getRowCount(), matrix.getColumnCount()));
  }

  private List<LaboratoryReportDtos.MatrixCategorySummary> matrixSummary(
      List<LaboratoryReportDtos.MatrixCell> cells, int rowCount, int columnCount) {
    long total = (long) rowCount * columnCount;
    if (total == 0) {
      return List.of();
    }
    return cells.stream()
        .map(LaboratoryReportDtos.MatrixCell::category)
        .filter(java.util.Objects::nonNull)
        .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()))
        .entrySet()
        .stream()
        .sorted(Map.Entry.comparingByKey(Comparator.naturalOrder()))
        .map(
            entry ->
                new LaboratoryReportDtos.MatrixCategorySummary(
                    entry.getKey(),
                    entry.getValue(),
                    BigDecimal.valueOf(entry.getValue() * 100)
                        .divide(BigDecimal.valueOf(total), 2, RoundingMode.HALF_UP)))
        .toList();
  }

  private Map<String, String> categoryLabels(UUID templateId) {
    if (templateId == null) {
      return Map.of();
    }
    return technicalCatalogService.getTemplate(templateId).categories().stream()
        .collect(
            Collectors.toMap(
                category -> category.code().toUpperCase(Locale.ROOT),
                category -> category.label(),
                (first, ignored) -> first));
  }

  private String categoryLabel(String category, Map<String, String> categoryLabels) {
    if (category == null) {
      return null;
    }
    return categoryLabels.getOrDefault(category.toUpperCase(Locale.ROOT), category);
  }
}
