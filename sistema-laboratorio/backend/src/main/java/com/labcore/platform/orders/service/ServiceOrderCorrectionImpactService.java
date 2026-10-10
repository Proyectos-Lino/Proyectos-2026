package com.labcore.platform.orders;

import com.labcore.platform.catalog.CatalogService;
import com.labcore.platform.client.ClientService;
import com.labcore.platform.common.exception.BusinessRuleViolationException;
import com.labcore.platform.results.ResultEntry;
import com.labcore.platform.results.ResultEntryRepository;
import com.labcore.platform.workorders.WorkOrderHistory;
import com.labcore.platform.workorders.WorkOrderHistoryAction;
import com.labcore.platform.workorders.WorkOrderHistoryRepository;
import com.labcore.platform.workorders.WorkOrderSample;
import com.labcore.platform.workorders.WorkOrderSampleRepository;
import com.labcore.platform.workorders.WorkOrderStatus;
import com.labcore.platform.workorders.WorkOrderTest;
import com.labcore.platform.workorders.WorkOrderTestRepository;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class ServiceOrderCorrectionImpactService {

  private final ServiceOrderSampleRepository sampleRepository;
  private final ServiceOrderSampleTestRepository requestedTestRepository;
  private final WorkOrderSampleRepository workOrderSampleRepository;
  private final WorkOrderTestRepository workOrderTestRepository;
  private final ResultEntryRepository resultEntryRepository;
  private final WorkOrderHistoryRepository workOrderHistoryRepository;
  private final ClientService clientService;
  private final CatalogService catalogService;

  public ServiceOrderCorrectionImpactService(
      ServiceOrderSampleRepository sampleRepository,
      ServiceOrderSampleTestRepository requestedTestRepository,
      WorkOrderSampleRepository workOrderSampleRepository,
      WorkOrderTestRepository workOrderTestRepository,
      ResultEntryRepository resultEntryRepository,
      WorkOrderHistoryRepository workOrderHistoryRepository,
      ClientService clientService,
      CatalogService catalogService) {
    this.sampleRepository = sampleRepository;
    this.requestedTestRepository = requestedTestRepository;
    this.workOrderSampleRepository = workOrderSampleRepository;
    this.workOrderTestRepository = workOrderTestRepository;
    this.resultEntryRepository = resultEntryRepository;
    this.workOrderHistoryRepository = workOrderHistoryRepository;
    this.clientService = clientService;
    this.catalogService = catalogService;
  }

  @Transactional(readOnly = true)
  public ServiceOrderDtos.CorrectionImpactResponse assess(
      ServiceOrder order, ServiceOrderDtos.UpdateOrderSamplesRequest request) {
    validateRequests(order, request.samples());
    return plan(order, request.samples()).response();
  }

  public void reconcile(
      ServiceOrder order, ServiceOrderDtos.UpdateOrderSamplesRequest request, String performedBy) {
    validateRequests(order, request.samples());
    ImpactPlan impact = plan(order, request.samples());
    if (impact.response().requiresResultInvalidation() && !request.confirmResultInvalidation()) {
      throw new BusinessRuleViolationException(impact.response().warningMessage());
    }
    if (impact.response().requiresResultInvalidation()
        && !Objects.equals(
            request.expectedAffectedResultCount(), impact.response().affectedResultCount())) {
      throw new BusinessRuleViolationException(
          "El impacto técnico cambió desde la última revisión. Vuelve a revisar la advertencia antes de confirmar.");
    }

    String reason =
        trimToNull(request.changeReason()) == null
            ? order.getCorrectionReason()
            : request.changeReason().trim();
    Map<UUID, ServiceOrderSample> existingSamples = activeSamples(order.getId());
    Map<UUID, Integer> originalSequences =
        existingSamples.values().stream()
            .collect(
                Collectors.toMap(
                    ServiceOrderSample::getId, ServiceOrderSample::getCommercialSequence));
    existingSamples
        .values()
        .forEach(
            sample -> sample.changeCommercialSequence(-10_000 - sample.getCommercialSequence()));
    sampleRepository.flush();

    Set<UUID> retainedSampleIds = new HashSet<>();
    int sequence = 1;
    for (ServiceOrderDtos.SampleRequest sampleRequest : request.samples()) {
      if (sampleRequest.sourceSampleId() == null) {
        createSample(order, sequence++, sampleRequest);
        continue;
      }

      ServiceOrderSample sample = existingSamples.get(sampleRequest.sourceSampleId());
      if (sample == null) {
        throw new BusinessRuleViolationException(
            "Una de las muestras editadas ya no pertenece a la orden o fue retirada.");
      }
      retainedSampleIds.add(sample.getId());
      boolean typeChanged = !sample.getSampleTypeId().equals(sampleRequest.sampleTypeId());
      updateSample(sample, sequence++, sampleRequest);
      reconcileTests(sample, sampleRequest, typeChanged, performedBy);
    }

    existingSamples.values().stream()
        .filter(sample -> !retainedSampleIds.contains(sample.getId()))
        .forEach(
            sample -> {
              requestedTestRepository.findByServiceOrderSampleId(sample.getId()).stream()
                  .filter(test -> test.getStatus() != RequestedTestStatus.CANCELLED)
                  .forEach(
                      test ->
                          cancelTechnicalChain(
                              test,
                              "La muestra fue retirada durante una corrección comercial.",
                              performedBy));
              sample.changeCommercialSequence(originalSequences.get(sample.getId()));
              sample.archive(reason);
            });

    String summary =
        impact.response().structuralChange()
            ? "%s Se invalidaron %d resultado(s) en %d orden(es) de trabajo; los datos históricos se conservaron."
                .formatted(
                    defaultSummary(impact.response()),
                    impact.response().affectedResultCount(),
                    impact.response().affectedWorkOrderCount())
            : "Muestras y datos de trazabilidad corregidos sin invalidar resultados técnicos.";
    order.registerCorrectionImpact(summary, impact.response().structuralChange());
  }

  private ImpactPlan plan(
      ServiceOrder order, List<ServiceOrderDtos.SampleRequest> requestedSamples) {
    Map<UUID, ServiceOrderSample> existingSamples = activeSamples(order.getId());
    Map<UUID, ServiceOrderDtos.SampleRequest> requestedById =
        requestedSamples.stream()
            .filter(sample -> sample.sourceSampleId() != null)
            .collect(
                Collectors.toMap(
                    ServiceOrderDtos.SampleRequest::sourceSampleId,
                    Function.identity(),
                    (first, duplicate) -> {
                      throw new BusinessRuleViolationException(
                          "Una muestra no puede aparecer más de una vez en la corrección.");
                    },
                    LinkedHashMap::new));

    Set<UUID> affectedRequestedTestIds = new HashSet<>();
    Map<UUID, String> reasons = new HashMap<>();
    boolean structuralChange =
        requestedSamples.stream().anyMatch(sample -> sample.sourceSampleId() == null);

    for (ServiceOrderSample existingSample : existingSamples.values()) {
      ServiceOrderDtos.SampleRequest requestedSample = requestedById.get(existingSample.getId());
      List<ServiceOrderSampleTest> existingTests = activeTests(existingSample.getId());
      if (requestedSample == null) {
        structuralChange = true;
        existingTests.forEach(
            test -> addAffected(affectedRequestedTestIds, reasons, test, "Muestra retirada"));
        continue;
      }

      boolean typeChanged =
          !existingSample.getSampleTypeId().equals(requestedSample.sampleTypeId());
      if (typeChanged) {
        structuralChange = true;
        existingTests.forEach(
            test ->
                addAffected(affectedRequestedTestIds, reasons, test, "Tipo de muestra modificado"));
      }

      Map<UUID, ServiceOrderSampleTest> existingTestsById =
          existingTests.stream()
              .collect(Collectors.toMap(ServiceOrderSampleTest::getId, Function.identity()));
      Set<UUID> retainedTestIds = new HashSet<>();
      for (ServiceOrderDtos.RequestedTestRequest requestedTest : requestedSample.requestedTests()) {
        if (requestedTest.sourceRequestedTestId() == null) {
          structuralChange = true;
          continue;
        }
        ServiceOrderSampleTest existingTest =
            existingTestsById.get(requestedTest.sourceRequestedTestId());
        if (existingTest == null) {
          throw new BusinessRuleViolationException(
              "Uno de los ensayos editados ya no pertenece a la muestra indicada.");
        }
        retainedTestIds.add(existingTest.getId());
        if (typeChanged || technicalDefinitionChanged(existingTest, requestedTest)) {
          structuralChange = true;
          addAffected(
              affectedRequestedTestIds,
              reasons,
              existingTest,
              typeChanged ? "Tipo de muestra modificado" : "Ensayo o método modificado");
        }
      }
      existingTests.stream()
          .filter(test -> !retainedTestIds.contains(test.getId()))
          .forEach(
              test -> {
                addAffected(affectedRequestedTestIds, reasons, test, "Ensayo retirado");
              });
      if (existingTests.stream().anyMatch(test -> !retainedTestIds.contains(test.getId()))) {
        structuralChange = true;
      }
    }

    List<WorkOrderTest> workOrderTests =
        affectedRequestedTestIds.isEmpty()
            ? List.of()
            : workOrderTestRepository.findBySourceSampleTestIdIn(affectedRequestedTestIds);
    Map<UUID, ResultEntry> resultsByTestId =
        resultEntryRepository
            .findByWorkOrderTestIdIn(workOrderTests.stream().map(WorkOrderTest::getId).toList())
            .stream()
            .collect(Collectors.toMap(ResultEntry::getWorkOrderTestId, Function.identity()));
    Map<UUID, WorkOrderSample> workOrderSamples =
        workOrderSampleRepository
            .findAllById(workOrderTests.stream().map(WorkOrderTest::getWorkOrderSampleId).toList())
            .stream()
            .collect(Collectors.toMap(WorkOrderSample::getId, Function.identity()));
    Map<UUID, ServiceOrderSampleTest> sourceTests =
        requestedTestRepository.findAllById(affectedRequestedTestIds).stream()
            .collect(Collectors.toMap(ServiceOrderSampleTest::getId, Function.identity()));
    Map<UUID, ServiceOrderSample> samples =
        sampleRepository
            .findAllById(
                sourceTests.values().stream()
                    .map(ServiceOrderSampleTest::getServiceOrderSampleId)
                    .toList())
            .stream()
            .collect(Collectors.toMap(ServiceOrderSample::getId, Function.identity()));

    List<ServiceOrderDtos.CorrectionImpactItem> items = new ArrayList<>();
    for (UUID sourceTestId : affectedRequestedTestIds) {
      ServiceOrderSampleTest sourceTest = sourceTests.get(sourceTestId);
      if (sourceTest == null) continue;
      ServiceOrderSample sample = samples.get(sourceTest.getServiceOrderSampleId());
      int resultCount =
          (int)
              workOrderTests.stream()
                  .filter(test -> test.getSourceSampleTestId().equals(sourceTestId))
                  .filter(test -> resultsByTestId.containsKey(test.getId()))
                  .count();
      items.add(
          new ServiceOrderDtos.CorrectionImpactItem(
              sample == null ? null : sample.getId(),
              sourceTestId,
              sampleLabel(sample),
              catalogService.getTest(sourceTest.getTestDefinitionId()).getName(),
              reasons.get(sourceTestId),
              resultCount));
    }
    int resultCount = resultsByTestId.size();
    int workOrderCount =
        (int)
            workOrderTests.stream()
                .map(WorkOrderTest::getWorkOrderSampleId)
                .map(workOrderSamples::get)
                .filter(Objects::nonNull)
                .map(WorkOrderSample::getWorkOrderId)
                .distinct()
                .count();
    String warning =
        resultCount == 0
            ? "Los cambios técnicos requieren nuevas asignaciones, pero no invalidarán resultados registrados."
            : "Esta modificación invalidará %d resultado(s) registrado(s) en %d orden(es) de trabajo. Los valores anteriores permanecerán en la auditoría, pero dejarán de utilizarse en el flujo y en los informes."
                .formatted(resultCount, workOrderCount);
    return new ImpactPlan(
        new ServiceOrderDtos.CorrectionImpactResponse(
            structuralChange, resultCount > 0, workOrderCount, resultCount, items, warning));
  }

  private void reconcileTests(
      ServiceOrderSample sample,
      ServiceOrderDtos.SampleRequest sampleRequest,
      boolean typeChanged,
      String performedBy) {
    Map<UUID, ServiceOrderSampleTest> existing =
        activeTests(sample.getId()).stream()
            .collect(Collectors.toMap(ServiceOrderSampleTest::getId, Function.identity()));
    Set<UUID> retained =
        sampleRequest.requestedTests().stream()
            .map(ServiceOrderDtos.RequestedTestRequest::sourceRequestedTestId)
            .filter(Objects::nonNull)
            .collect(Collectors.toSet());
    boolean cancelledRemovedTests = false;
    for (ServiceOrderSampleTest test : existing.values()) {
      if (!retained.contains(test.getId())) {
        cancelTechnicalChain(
            test, "El ensayo fue retirado durante una corrección comercial.", performedBy);
        cancelledRemovedTests = true;
      }
    }
    if (cancelledRemovedTests) {
      requestedTestRepository.flush();
    }

    for (ServiceOrderDtos.RequestedTestRequest request : sampleRequest.requestedTests()) {
      ServiceOrderSampleTest current =
          request.sourceRequestedTestId() == null
              ? null
              : existing.get(request.sourceRequestedTestId());
      if (current != null && !typeChanged && !technicalDefinitionChanged(current, request)) {
        continue;
      }
      if (current != null) {
        cancelTechnicalChain(
            current,
            typeChanged
                ? "El tipo técnico de la muestra fue modificado durante una corrección comercial."
                : "El ensayo o método fue reemplazado durante una corrección comercial.",
            performedBy);
        requestedTestRepository.flush();
      }
      requestedTestRepository.save(
          new ServiceOrderSampleTest(
              sample.getId(),
              request.serviceId(),
              request.testDefinitionId(),
              request.testMethodId()));
    }
  }

  private void cancelTechnicalChain(
      ServiceOrderSampleTest sourceTest, String reason, String performedBy) {
    if (sourceTest.getStatus() == RequestedTestStatus.CANCELLED) return;
    sourceTest.cancel();
    List<WorkOrderTest> workOrderTests =
        workOrderTestRepository.findBySourceSampleTestIdIn(List.of(sourceTest.getId()));
    workOrderTests.forEach(
        test -> {
          var result = resultEntryRepository.findByWorkOrderTestId(test.getId());
          boolean hadResult = result.isPresent();
          test.invalidate(reason, performedBy);
          result.ifPresent(entry -> entry.invalidate(reason, performedBy));
          workOrderSampleRepository
              .findById(test.getWorkOrderSampleId())
              .ifPresent(
                  workOrderSample ->
                      workOrderHistoryRepository.save(
                          new WorkOrderHistory(
                              workOrderSample.getWorkOrderId(),
                              WorkOrderHistoryAction.TECHNICAL_DATA_INVALIDATED,
                              WorkOrderStatus.COMMERCIAL_CORRECTION,
                              WorkOrderStatus.COMMERCIAL_CORRECTION,
                              "%s de la muestra %s invalidado por corrección comercial%s. Motivo: %s"
                                  .formatted(
                                      catalogService
                                          .getTest(sourceTest.getTestDefinitionId())
                                          .getName(),
                                      workOrderSample.getLaboratoryCode(),
                                      hadResult
                                          ? "; el resultado previo se conserva en auditoría"
                                          : "",
                                      reason),
                              performedBy)));
        });
  }

  private void createSample(
      ServiceOrder order, int sequence, ServiceOrderDtos.SampleRequest request) {
    ServiceOrderSample sample =
        sampleRepository.save(
            new ServiceOrderSample(
                order.getId(),
                sequence,
                trimToNull(request.clientIdentification()),
                request.producerId(),
                request.productName().trim(),
                trimToNull(request.varietyName()),
                request.sampleTypeId(),
                trimToNull(request.physicalDescription()),
                trimToNull(request.samplingLocation()),
                trimToNull(request.origin()),
                trimToNull(request.samplerName()),
                request.sampledAt(),
                request.quantity(),
                request.measurementUnitId(),
                trimToNull(request.containerDescription()),
                trimToNull(request.batchNumber()),
                request.productionYear(),
                trimToNull(request.brand()),
                trimToNull(request.originCertificate()),
                trimToNull(request.sanitaryRegistration()),
                trimToNull(request.cropAge()),
                request.carrierCount(),
                request.budCount(),
                request.counterSampleInCustody(),
                trimToNull(request.observations())));
    request
        .requestedTests()
        .forEach(
            test ->
                requestedTestRepository.save(
                    new ServiceOrderSampleTest(
                        sample.getId(),
                        test.serviceId(),
                        test.testDefinitionId(),
                        test.testMethodId())));
  }

  private void updateSample(
      ServiceOrderSample sample, int sequence, ServiceOrderDtos.SampleRequest request) {
    sample.update(
        sequence,
        trimToNull(request.clientIdentification()),
        request.producerId(),
        request.productName().trim(),
        trimToNull(request.varietyName()),
        request.sampleTypeId(),
        trimToNull(request.physicalDescription()),
        trimToNull(request.samplingLocation()),
        trimToNull(request.origin()),
        trimToNull(request.samplerName()),
        request.sampledAt(),
        request.quantity(),
        request.measurementUnitId(),
        trimToNull(request.containerDescription()),
        trimToNull(request.batchNumber()),
        request.productionYear(),
        trimToNull(request.brand()),
        trimToNull(request.originCertificate()),
        trimToNull(request.sanitaryRegistration()),
        trimToNull(request.cropAge()),
        request.carrierCount(),
        request.budCount(),
        request.counterSampleInCustody(),
        trimToNull(request.observations()));
  }

  private void validateRequests(ServiceOrder order, List<ServiceOrderDtos.SampleRequest> requests) {
    for (ServiceOrderDtos.SampleRequest sample : requests) {
      catalogService.getSampleType(sample.sampleTypeId());
      if (sample.producerId() != null) clientService.getActiveProducerRequired(sample.producerId());
      if (sample.measurementUnitId() != null) catalogService.getUnit(sample.measurementUnitId());
      if (sample.requestedTests().stream()
              .map(ServiceOrderDtos.RequestedTestRequest::testDefinitionId)
              .distinct()
              .count()
          != sample.requestedTests().size()) {
        throw new BusinessRuleViolationException(
            "Una muestra no puede incluir el mismo ensayo más de una vez.");
      }
      for (ServiceOrderDtos.RequestedTestRequest test : sample.requestedTests()) {
        var service = catalogService.getService(test.serviceId());
        if (!service.getLaboratoryAreaId().equals(order.getLaboratoryAreaId())) {
          throw new BusinessRuleViolationException(
              "Los ensayos seleccionados deben pertenecer al área de la orden.");
        }
        if (!catalogService.serviceIncludesTest(test.serviceId(), test.testDefinitionId())) {
          throw new BusinessRuleViolationException(
              "El ensayo seleccionado no pertenece al servicio indicado.");
        }
        catalogService.getMethod(test.testMethodId(), test.testDefinitionId());
      }
    }
  }

  private Map<UUID, ServiceOrderSample> activeSamples(UUID orderId) {
    return sampleRepository
        .findByServiceOrderIdAndActiveTrueOrderByCommercialSequenceAsc(orderId)
        .stream()
        .collect(Collectors.toMap(ServiceOrderSample::getId, Function.identity()));
  }

  private List<ServiceOrderSampleTest> activeTests(UUID sampleId) {
    return requestedTestRepository.findByServiceOrderSampleId(sampleId).stream()
        .filter(test -> test.getStatus() != RequestedTestStatus.CANCELLED)
        .toList();
  }

  private boolean technicalDefinitionChanged(
      ServiceOrderSampleTest existing, ServiceOrderDtos.RequestedTestRequest requested) {
    return !existing.getLaboratoryServiceId().equals(requested.serviceId())
        || !existing.getTestDefinitionId().equals(requested.testDefinitionId())
        || !existing.getTestMethodId().equals(requested.testMethodId());
  }

  private void addAffected(
      Set<UUID> affected, Map<UUID, String> reasons, ServiceOrderSampleTest test, String reason) {
    affected.add(test.getId());
    reasons.putIfAbsent(test.getId(), reason);
  }

  private String sampleLabel(ServiceOrderSample sample) {
    if (sample == null) return "Muestra no disponible";
    return "Muestra %d - %s".formatted(sample.getCommercialSequence(), sample.getProductName());
  }

  private String defaultSummary(ServiceOrderDtos.CorrectionImpactResponse response) {
    return response.affectedItems().isEmpty()
        ? "Se incorporaron nuevas definiciones técnicas."
        : response.affectedItems().stream()
            .map(
                item -> "%s: %s (%s)".formatted(item.sampleLabel(), item.testName(), item.reason()))
            .collect(Collectors.joining("; ", "Cambios técnicos: ", "."));
  }

  private String trimToNull(String value) {
    return value == null || value.isBlank() ? null : value.trim();
  }

  private record ImpactPlan(ServiceOrderDtos.CorrectionImpactResponse response) {}
}
