package com.labcore.platform.orders;

import com.labcore.platform.catalog.CatalogService;
import com.labcore.platform.catalog.LaboratoryArea;
import com.labcore.platform.client.Client;
import com.labcore.platform.client.ClientService;
import com.labcore.platform.common.exception.BusinessRuleViolationException;
import com.labcore.platform.common.exception.ResourceNotFoundException;
import com.labcore.platform.workorders.TechnicalSample;
import com.labcore.platform.workorders.TechnicalSampleRepository;
import com.labcore.platform.workorders.WorkOrderRepository;
import com.labcore.platform.workorders.WorkOrderStatus;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class ServiceOrderService {

  private final ServiceOrderRepository orderRepository;
  private final ServiceOrderSampleRepository sampleRepository;
  private final ServiceOrderSampleTestRepository requestedTestRepository;
  private final ServiceOrderHistoryRepository historyRepository;
  private final ClientService clientService;
  private final CatalogService catalogService;
  private final DocumentCodeGenerator codeGenerator;
  private final WorkOrderRepository workOrderRepository;
  private final TechnicalSampleRepository technicalSampleRepository;
  private final ServiceOrderWorkflowEvaluator workflowEvaluator;
  private final ServiceOrderDocumentService documentService;
  private final ServiceOrderCorrectionImpactService correctionImpactService;

  public ServiceOrderService(
      ServiceOrderRepository orderRepository,
      ServiceOrderSampleRepository sampleRepository,
      ServiceOrderSampleTestRepository requestedTestRepository,
      ServiceOrderHistoryRepository historyRepository,
      ClientService clientService,
      CatalogService catalogService,
      DocumentCodeGenerator codeGenerator,
      WorkOrderRepository workOrderRepository,
      TechnicalSampleRepository technicalSampleRepository,
      ServiceOrderWorkflowEvaluator workflowEvaluator,
      ServiceOrderDocumentService documentService,
      ServiceOrderCorrectionImpactService correctionImpactService) {
    this.orderRepository = orderRepository;
    this.sampleRepository = sampleRepository;
    this.requestedTestRepository = requestedTestRepository;
    this.historyRepository = historyRepository;
    this.clientService = clientService;
    this.catalogService = catalogService;
    this.codeGenerator = codeGenerator;
    this.workOrderRepository = workOrderRepository;
    this.technicalSampleRepository = technicalSampleRepository;
    this.workflowEvaluator = workflowEvaluator;
    this.documentService = documentService;
    this.correctionImpactService = correctionImpactService;
  }

  public ServiceOrderDtos.ServiceOrderDetail create(
      ServiceOrderDtos.CreateServiceOrderRequest request, String performedBy) {
    validateOrderRequest(request);
    clientService.getActiveClientRequired(request.clientId());
    catalogService.getArea(request.laboratoryAreaId());
    ServiceOrder order =
        orderRepository.save(
            new ServiceOrder(
                codeGenerator.next("OS"),
                request.clientId(),
                request.laboratoryAreaId(),
                nullableTrim(request.reportName()),
                nullableTrim(request.reportEmail()),
                nullableTrim(request.reportAddress()),
                request.deliveryMethod(),
                nullableTrim(request.pickupPerson()),
                request.plannedDeliveryAt(),
                nullableTrim(request.proformaNumber()),
                nullableTrim(request.technicalProposalNumber()),
                request.developmentDays(),
                request.outsourced(),
                request.postAnalysisCustody(),
                nullableTrim(request.generalObservations())));

    saveSamples(order, request.samples());
    recordHistory(
        order,
        ServiceOrderHistoryAction.CREATED,
        null,
        order.getStatus(),
        "Orden de servicio registrada como borrador.",
        performedBy);
    return getDetail(order.getId());
  }

  @Transactional(readOnly = true)
  public Page<ServiceOrderDtos.ServiceOrderSummary> getPage(
      String search,
      ServiceOrderStatus status,
      UUID clientId,
      UUID laboratoryAreaId,
      Instant plannedDeliveryFrom,
      Instant plannedDeliveryTo,
      Instant createdFrom,
      Instant createdTo,
      Pageable pageable) {
    Specification<ServiceOrder> specification = searchSpecification(search);
    if (status != null) {
      specification =
          specification.and((root, query, builder) -> builder.equal(root.get("status"), status));
    }
    if (clientId != null) {
      specification =
          specification.and(
              (root, query, builder) -> builder.equal(root.get("clientId"), clientId));
    }
    if (laboratoryAreaId != null) {
      specification =
          specification.and(
              (root, query, builder) ->
                  builder.equal(root.get("laboratoryAreaId"), laboratoryAreaId));
    }
    if (plannedDeliveryFrom != null) {
      specification =
          specification.and(
              (root, query, builder) ->
                  builder.greaterThanOrEqualTo(root.get("plannedDeliveryAt"), plannedDeliveryFrom));
    }
    if (plannedDeliveryTo != null) {
      specification =
          specification.and(
              (root, query, builder) ->
                  builder.lessThanOrEqualTo(root.get("plannedDeliveryAt"), plannedDeliveryTo));
    }
    if (createdFrom != null) {
      specification =
          specification.and(
              (root, query, builder) ->
                  builder.greaterThanOrEqualTo(root.get("createdAt"), createdFrom));
    }
    if (createdTo != null) {
      specification =
          specification.and(
              (root, query, builder) ->
                  builder.lessThanOrEqualTo(root.get("createdAt"), createdTo));
    }
    Page<ServiceOrder> orders = orderRepository.findAll(specification, pageable);
    return toSummaryPage(orders);
  }

  @Transactional(readOnly = true)
  public Page<ServiceOrderDtos.ServiceOrderSummary> getPageByClient(
      UUID clientId, Pageable pageable) {
    return toSummaryPage(orderRepository.findByClientIdOrderByCreatedAtDesc(clientId, pageable));
  }

  @Transactional(readOnly = true)
  public Page<ServiceOrderDtos.ProducerSampleHistory> getPageByProducer(
      UUID producerId, Pageable pageable) {
    Page<ServiceOrderSample> samples =
        sampleRepository.findByProducerIdOrderByCreatedAtDesc(producerId, pageable);
    Map<UUID, ServiceOrder> orders =
        orderRepository
            .findAllById(
                samples.stream().map(ServiceOrderSample::getServiceOrderId).distinct().toList())
            .stream()
            .collect(java.util.stream.Collectors.toMap(ServiceOrder::getId, Function.identity()));
    return samples.map(
        sample -> {
          ServiceOrder order = orders.get(sample.getServiceOrderId());
          return new ServiceOrderDtos.ProducerSampleHistory(
              order.getId(),
              order.getCode(),
              order.getStatus(),
              order.getCreatedAt(),
              sample.getId(),
              sample.getCommercialSequence(),
              sample.getClientIdentification(),
              sample.getProductName(),
              sample.getVarietyName());
        });
  }

  private Page<ServiceOrderDtos.ServiceOrderSummary> toSummaryPage(Page<ServiceOrder> orders) {
    Map<UUID, Client> clients =
        orders.stream()
            .map(ServiceOrder::getClientId)
            .distinct()
            .map(clientService::getRequired)
            .collect(java.util.stream.Collectors.toMap(Client::getId, Function.identity()));
    Map<UUID, LaboratoryArea> areas =
        orders.stream()
            .map(ServiceOrder::getLaboratoryAreaId)
            .distinct()
            .map(catalogService::getArea)
            .collect(java.util.stream.Collectors.toMap(LaboratoryArea::getId, Function.identity()));
    return orders.map(
        order ->
            new ServiceOrderDtos.ServiceOrderSummary(
                order.getId(),
                order.getCode(),
                clients.get(order.getClientId()).getLegalName(),
                areas.get(order.getLaboratoryAreaId()).getName(),
                order.getStatus(),
                order.getPlannedDeliveryAt(),
                order.getCreatedAt(),
                order.getProformaNumber(),
                order.isOutsourced(),
                sampleRepository
                    .findByServiceOrderIdAndActiveTrueOrderByCommercialSequenceAsc(order.getId())
                    .size()));
  }

  @Transactional(readOnly = true)
  public ServiceOrderDtos.ServiceOrderDetail getDetail(UUID orderId) {
    ServiceOrder order = getRequired(orderId);
    Client client = clientService.getRequired(order.getClientId());
    LaboratoryArea laboratoryArea = catalogService.getArea(order.getLaboratoryAreaId());
    List<ServiceOrderSample> samples =
        sampleRepository.findByServiceOrderIdAndActiveTrueOrderByCommercialSequenceAsc(orderId);
    Map<UUID, Client> producers =
        samples.stream()
            .map(ServiceOrderSample::getProducerId)
            .filter(java.util.Objects::nonNull)
            .distinct()
            .map(clientService::getRequired)
            .collect(java.util.stream.Collectors.toMap(Client::getId, Function.identity()));
    Map<UUID, List<ServiceOrderSampleTest>> testsBySample =
        requestedTestRepository
            .findByServiceOrderSampleIdIn(samples.stream().map(ServiceOrderSample::getId).toList())
            .stream()
            .filter(test -> test.getStatus() != RequestedTestStatus.CANCELLED)
            .collect(
                java.util.stream.Collectors.groupingBy(
                    ServiceOrderSampleTest::getServiceOrderSampleId));
    Map<UUID, TechnicalSample> technicalSamplesBySourceId =
        technicalSampleRepository
            .findByServiceOrderSampleIdIn(samples.stream().map(ServiceOrderSample::getId).toList())
            .stream()
            .collect(
                java.util.stream.Collectors.toMap(
                    TechnicalSample::getServiceOrderSampleId, Function.identity()));
    List<ServiceOrderDtos.SampleResponse> sampleResponses =
        samples.stream()
            .map(
                sample ->
                    new ServiceOrderDtos.SampleResponse(
                        sample.getId(),
                        sample.getCommercialSequence(),
                        sample.getClientIdentification(),
                        sample.getProducerId(),
                        producerName(producers.get(sample.getProducerId())),
                        participantDocument(producers.get(sample.getProducerId())),
                        sample.getProductName(),
                        sample.getVarietyName(),
                        sample.getSampleTypeId(),
                        sample.getPhysicalDescription(),
                        sample.getSamplingLocation(),
                        sample.getOrigin(),
                        sample.getSamplerName(),
                        sample.getSampledAt(),
                        sample.getQuantity(),
                        sample.getMeasurementUnitId(),
                        sample.getContainerDescription(),
                        sample.getBatchNumber(),
                        sample.getProductionYear(),
                        sample.getBrand(),
                        sample.getOriginCertificate(),
                        sample.getSanitaryRegistration(),
                        sample.getCropAge(),
                        sample.getCarrierCount(),
                        sample.getBudCount(),
                        sample.isCounterSampleInCustody(),
                        sample.getObservations(),
                        toTechnicalIdentification(technicalSamplesBySourceId.get(sample.getId())),
                        testsBySample.getOrDefault(sample.getId(), List.of()).stream()
                            .map(
                                test ->
                                    new ServiceOrderDtos.RequestedTestResponse(
                                        test.getId(),
                                        test.getLaboratoryServiceId(),
                                        test.getTestDefinitionId(),
                                        test.getTestMethodId(),
                                        test.getStatus()))
                            .toList()))
            .toList();
    List<ServiceOrderSampleTest> requestedTests =
        testsBySample.values().stream().flatMap(Collection::stream).toList();
    ServiceOrderDtos.TechnicalProgress technicalProgress =
        workflowEvaluator.evaluate(order, requestedTests);
    return new ServiceOrderDtos.ServiceOrderDetail(
        order.getId(),
        order.getCode(),
        order.getClientId(),
        client.getLegalName(),
        client.getDocumentType() + " " + client.getDocumentNumber(),
        order.getLaboratoryAreaId(),
        laboratoryArea.getName(),
        order.getStatus(),
        order.getReportName(),
        order.getReportEmail(),
        order.getReportAddress(),
        order.getDeliveryMethod(),
        order.getPickupPerson(),
        order.getPlannedDeliveryAt(),
        order.getProformaNumber(),
        order.getTechnicalProposalNumber(),
        order.getDevelopmentDays(),
        order.isOutsourced(),
        order.getPostAnalysisCustody(),
        order.getGeneralObservations(),
        order.getConfirmedAt(),
        order.getFinalizedAt(),
        order.getCancelledAt(),
        order.getCancellationReason(),
        order.getStatusBeforeCorrection(),
        order.getCorrectionReason(),
        order.getCorrectionRequestedBy(),
        order.getCorrectionRequestedAt(),
        order.getCorrectionSubmittedBy(),
        order.getCorrectionSubmittedAt(),
        order.getCorrectionChangeNote(),
        order.getCorrectionReviewedBy(),
        order.getCorrectionReviewedAt(),
        order.getCorrectionImpactSummary(),
        order.isCorrectionRequiresTechnicalWork(),
        order.getCreatedAt(),
        order.getUpdatedAt(),
        sampleResponses,
        technicalProgress);
  }

  private ServiceOrderDtos.TechnicalIdentificationResponse toTechnicalIdentification(
      TechnicalSample technicalSample) {
    if (technicalSample == null) {
      return null;
    }
    return new ServiceOrderDtos.TechnicalIdentificationResponse(
        technicalSample.getId(),
        technicalSample.getTechnicalSampleTypeId(),
        catalogService.getSampleType(technicalSample.getTechnicalSampleTypeId()).getName(),
        technicalSample.getLaboratoryCode(),
        technicalSample.getClassifiedBy(),
        technicalSample.getClassifiedAt());
  }

  public ServiceOrderDtos.ServiceOrderDetail updateCommercialDetails(
      UUID orderId, ServiceOrderDtos.UpdateCommercialDetailsRequest request, String performedBy) {
    ServiceOrder order = getRequired(orderId);
    if (order.getStatus() != ServiceOrderStatus.DRAFT
        && order.getStatus() != ServiceOrderStatus.COMMERCIAL_CORRECTION) {
      throw new BusinessRuleViolationException(
          "Los datos comerciales solo se pueden modificar en borrador o durante una corrección comercial.");
    }
    if (request.clientId() != null && !request.clientId().equals(order.getClientId())) {
      clientService.getActiveClientRequired(request.clientId());
      order.updateClient(request.clientId());
    }
    validateCommercialDetails(
        request.deliveryMethod(), request.pickupPerson(), request.plannedDeliveryAt());
    order.updateCommercialDetails(
        nullableTrim(request.reportName()),
        nullableTrim(request.reportEmail()),
        nullableTrim(request.reportAddress()),
        request.deliveryMethod(),
        nullableTrim(request.pickupPerson()),
        request.plannedDeliveryAt(),
        nullableTrim(request.proformaNumber()),
        nullableTrim(request.technicalProposalNumber()),
        request.developmentDays(),
        request.outsourced(),
        request.postAnalysisCustody(),
        nullableTrim(request.generalObservations()));
    recordHistory(
        order,
        ServiceOrderHistoryAction.COMMERCIAL_DETAILS_UPDATED,
        order.getStatus(),
        order.getStatus(),
        "Datos comerciales actualizados.",
        performedBy);
    return getDetail(orderId);
  }

  public ServiceOrderDtos.ServiceOrderDetail updateSamples(
      UUID orderId, ServiceOrderDtos.UpdateOrderSamplesRequest request, String performedBy) {
    ServiceOrder order = getRequired(orderId);
    if (order.getStatus() != ServiceOrderStatus.DRAFT
        && order.getStatus() != ServiceOrderStatus.COMMERCIAL_CORRECTION) {
      throw new BusinessRuleViolationException(
          "Las muestras y ensayos solo se pueden modificar en borrador o durante una corrección comercial.");
    }

    if (order.getStatus() == ServiceOrderStatus.COMMERCIAL_CORRECTION) {
      correctionImpactService.reconcile(order, request, performedBy);
      recordHistory(
          order,
          ServiceOrderHistoryAction.SAMPLES_UPDATED,
          order.getStatus(),
          order.getStatus(),
          order.getCorrectionImpactSummary() == null
              ? "Muestras y ensayos corregidos sin afectar resultados existentes."
              : order.getCorrectionImpactSummary(),
          performedBy);
      return getDetail(orderId);
    }

    List<ServiceOrderSample> existingSamples =
        sampleRepository.findByServiceOrderIdOrderByCommercialSequenceAsc(orderId);
    List<ServiceOrderSampleTest> existingTests =
        requestedTestRepository.findByServiceOrderSampleIdIn(
            existingSamples.stream().map(ServiceOrderSample::getId).toList());
    requestedTestRepository.deleteAllInBatch(existingTests);
    sampleRepository.deleteAllInBatch(existingSamples);
    saveSamples(order, request.samples());
    recordHistory(
        order,
        ServiceOrderHistoryAction.SAMPLES_UPDATED,
        order.getStatus(),
        order.getStatus(),
        "Muestras y ensayos solicitados actualizados.",
        performedBy);
    return getDetail(orderId);
  }

  public void confirmOrder(UUID orderId, String performedBy) {
    ServiceOrder order = getRequired(orderId);
    if (order.getStatus() != ServiceOrderStatus.DRAFT) {
      throw new BusinessRuleViolationException(
          "Solo se pueden confirmar órdenes que todavía están en borrador.");
    }
    validateOrderDefinition(orderId);
    ServiceOrderStatus previousStatus = order.getStatus();
    order.confirmForLaboratory();
    recordHistory(
        order,
        ServiceOrderHistoryAction.CONFIRMED,
        previousStatus,
        order.getStatus(),
        "Orden confirmada y enviada al laboratorio. Sus datos comerciales, muestras y ensayos quedaron bloqueados.",
        performedBy);
    documentService.generateForConfirmation(orderId, performedBy);
  }

  public void finalizeOrder(UUID orderId, String performedBy) {
    ServiceOrder order = getRequired(orderId);
    if (order.getStatus() != ServiceOrderStatus.IN_PROGRESS) {
      throw new BusinessRuleViolationException("Solo se pueden finalizar órdenes en proceso.");
    }
    List<ServiceOrderSampleTest> requestedTests = requestedTestsFor(orderId);
    ServiceOrderDtos.TechnicalProgress progress = workflowEvaluator.evaluate(order, requestedTests);
    if (!progress.canFinalize()) {
      throw new BusinessRuleViolationException(
          "La orden aún no puede finalizarse: " + String.join(" ", progress.blockingReasons()));
    }
    ServiceOrderStatus previousStatus = order.getStatus();
    order.completeTechnicalProcess();
    recordHistory(
        order,
        ServiceOrderHistoryAction.FINALIZED,
        previousStatus,
        order.getStatus(),
        "Proceso técnico completado: %d de %d ensayo(s) validados y todas las órdenes de trabajo cerradas."
            .formatted(progress.validatedTests(), progress.totalRequestedTests()),
        performedBy);
  }

  public void cancelOrder(
      UUID orderId, ServiceOrderDtos.CancelServiceOrderRequest request, String performedBy) {
    ServiceOrder order = getRequired(orderId);
    if (order.getStatus() == ServiceOrderStatus.CANCELLED) {
      throw new BusinessRuleViolationException("La orden ya fue anulada.");
    }
    if (order.getStatus() == ServiceOrderStatus.FINALIZED) {
      throw new BusinessRuleViolationException("No se puede anular una orden finalizada.");
    }
    boolean hasNonCancelledWorkOrder =
        workOrderRepository.findByServiceOrderIdOrderByCreatedAtAsc(orderId).stream()
            .anyMatch(workOrder -> workOrder.getStatus() != WorkOrderStatus.CANCELLED);
    if (hasNonCancelledWorkOrder) {
      throw new BusinessRuleViolationException(
          "Antes de anular la orden de servicio debe resolver sus órdenes de trabajo activas.");
    }
    ServiceOrderStatus previousStatus = order.getStatus();
    order.cancel(nullableTrim(request.reason()));
    recordHistory(
        order,
        ServiceOrderHistoryAction.CANCELLED,
        previousStatus,
        order.getStatus(),
        order.getCancellationReason(),
        performedBy);
  }

  @Transactional(readOnly = true)
  public Page<ServiceOrderDtos.ServiceOrderHistoryEntry> getHistory(
      UUID orderId, Pageable pageable) {
    getRequired(orderId);
    return historyRepository
        .findByServiceOrderIdOrderByOccurredAtDesc(orderId, pageable)
        .map(ServiceOrderDtos.ServiceOrderHistoryEntry::from);
  }

  public ServiceOrder getRequired(UUID orderId) {
    return orderRepository
        .findById(orderId)
        .orElseThrow(() -> new ResourceNotFoundException("la orden de servicio", orderId));
  }

  public List<ServiceOrderSample> getSamples(UUID orderId) {
    return sampleRepository.findByServiceOrderIdAndActiveTrueOrderByCommercialSequenceAsc(orderId);
  }

  public List<ServiceOrderSampleTest> getRequestedTests(Collection<UUID> sampleIds) {
    return requestedTestRepository.findByServiceOrderSampleIdIn(sampleIds).stream()
        .filter(test -> test.getStatus() != RequestedTestStatus.CANCELLED)
        .toList();
  }

  private List<ServiceOrderSampleTest> requestedTestsFor(UUID orderId) {
    List<ServiceOrderSample> samples =
        sampleRepository.findByServiceOrderIdAndActiveTrueOrderByCommercialSequenceAsc(orderId);
    if (samples.isEmpty()) {
      return List.of();
    }
    return requestedTestRepository
        .findByServiceOrderSampleIdIn(samples.stream().map(ServiceOrderSample::getId).toList())
        .stream()
        .filter(test -> test.getStatus() != RequestedTestStatus.CANCELLED)
        .toList();
  }

  private void validateOrderDefinition(UUID orderId) {
    List<ServiceOrderSample> samples =
        sampleRepository.findByServiceOrderIdAndActiveTrueOrderByCommercialSequenceAsc(orderId);
    if (samples.isEmpty()) {
      throw new BusinessRuleViolationException("La orden requiere por lo menos una muestra.");
    }
    Map<UUID, List<ServiceOrderSampleTest>> tests =
        requestedTestRepository
            .findByServiceOrderSampleIdIn(samples.stream().map(ServiceOrderSample::getId).toList())
            .stream()
            .collect(
                java.util.stream.Collectors.groupingBy(
                    ServiceOrderSampleTest::getServiceOrderSampleId));
    if (samples.stream()
        .anyMatch(sample -> tests.getOrDefault(sample.getId(), List.of()).isEmpty())) {
      throw new BusinessRuleViolationException(
          "Cada muestra debe tener por lo menos un ensayo solicitado.");
    }
    if (samples.stream()
        .anyMatch(
            sample ->
                sample.getProductName() == null
                    || sample.getProductName().isBlank()
                    || sample.getSampleTypeId() == null)) {
      throw new BusinessRuleViolationException(
          "Cada muestra debe tener producto y tipo de muestra antes de confirmar.");
    }
    boolean hasDuplicateTests =
        tests.values().stream()
            .anyMatch(
                requestedTests ->
                    requestedTests.stream()
                            .map(ServiceOrderSampleTest::getTestDefinitionId)
                            .distinct()
                            .count()
                        != requestedTests.size());
    if (hasDuplicateTests) {
      throw new BusinessRuleViolationException(
          "Una muestra no puede incluir el mismo ensayo más de una vez.");
    }
  }

  private ServiceOrderSample createSample(
      UUID orderId, int sequence, ServiceOrderDtos.SampleRequest request) {
    catalogService.getSampleType(request.sampleTypeId());
    if (request.producerId() != null) {
      clientService.getActiveProducerRequired(request.producerId());
    }
    if (request.measurementUnitId() != null) catalogService.getUnit(request.measurementUnitId());
    return new ServiceOrderSample(
        orderId,
        sequence,
        nullableTrim(request.clientIdentification()),
        request.producerId(),
        nullableTrim(request.productName()),
        nullableTrim(request.varietyName()),
        request.sampleTypeId(),
        nullableTrim(request.physicalDescription()),
        nullableTrim(request.samplingLocation()),
        nullableTrim(request.origin()),
        nullableTrim(request.samplerName()),
        request.sampledAt(),
        request.quantity(),
        request.measurementUnitId(),
        nullableTrim(request.containerDescription()),
        nullableTrim(request.batchNumber()),
        request.productionYear(),
        nullableTrim(request.brand()),
        nullableTrim(request.originCertificate()),
        nullableTrim(request.sanitaryRegistration()),
        nullableTrim(request.cropAge()),
        request.carrierCount(),
        request.budCount(),
        request.counterSampleInCustody(),
        nullableTrim(request.observations()));
  }

  private void validateRequestedTest(ServiceOrderDtos.RequestedTestRequest request) {
    catalogService.getService(request.serviceId());
    catalogService.getTest(request.testDefinitionId());
    if (!catalogService.serviceIncludesTest(request.serviceId(), request.testDefinitionId())) {
      throw new BusinessRuleViolationException(
          "El ensayo seleccionado no pertenece al servicio indicado.");
    }
    catalogService.getMethod(request.testMethodId(), request.testDefinitionId());
  }

  private void saveSamples(
      ServiceOrder order, List<ServiceOrderDtos.SampleRequest> sampleRequests) {
    validateSampleRequests(order, sampleRequests);

    int sequence = 1;
    for (ServiceOrderDtos.SampleRequest sampleRequest : sampleRequests) {
      ServiceOrderSample sample =
          sampleRepository.save(createSample(order.getId(), sequence++, sampleRequest));
      for (ServiceOrderDtos.RequestedTestRequest testRequest : sampleRequest.requestedTests()) {
        requestedTestRepository.save(
            new ServiceOrderSampleTest(
                sample.getId(),
                testRequest.serviceId(),
                testRequest.testDefinitionId(),
                testRequest.testMethodId()));
      }
    }
  }

  private void validateSampleRequests(
      ServiceOrder order, List<ServiceOrderDtos.SampleRequest> sampleRequests) {
    for (ServiceOrderDtos.SampleRequest sampleRequest : sampleRequests) {
      catalogService.getSampleType(sampleRequest.sampleTypeId());
      if (sampleRequest.producerId() != null) {
        clientService.getActiveProducerRequired(sampleRequest.producerId());
      }
      if (sampleRequest.measurementUnitId() != null) {
        catalogService.getUnit(sampleRequest.measurementUnitId());
      }
      long distinctTestCount =
          sampleRequest.requestedTests().stream()
              .map(ServiceOrderDtos.RequestedTestRequest::testDefinitionId)
              .distinct()
              .count();
      if (distinctTestCount != sampleRequest.requestedTests().size()) {
        throw new BusinessRuleViolationException(
            "Una muestra no puede incluir el mismo ensayo más de una vez.");
      }
      for (ServiceOrderDtos.RequestedTestRequest testRequest : sampleRequest.requestedTests()) {
        var service = catalogService.getService(testRequest.serviceId());
        if (!service.getLaboratoryAreaId().equals(order.getLaboratoryAreaId())) {
          throw new BusinessRuleViolationException(
              "Los ensayos seleccionados deben pertenecer al área de la orden de servicio.");
        }
        validateRequestedTest(testRequest);
      }
    }
  }

  private void validateOrderRequest(ServiceOrderDtos.CreateServiceOrderRequest request) {
    validateCommercialDetails(
        request.deliveryMethod(), request.pickupPerson(), request.plannedDeliveryAt());
  }

  private void validateCommercialDetails(
      DeliveryMethod deliveryMethod, String pickupPerson, Instant plannedDeliveryAt) {
    if (plannedDeliveryAt.isBefore(Instant.now())) {
      throw new BusinessRuleViolationException(
          "La fecha de entrega programada no puede estar en el pasado.");
    }
    if (deliveryMethod == DeliveryMethod.PICKUP
        && (pickupPerson == null || pickupPerson.isBlank())) {
      throw new BusinessRuleViolationException("Debe indicar quién recogerá el informe.");
    }
  }

  private Specification<ServiceOrder> searchSpecification(String search) {
    if (search == null || search.isBlank()) {
      return (root, query, builder) -> builder.conjunction();
    }
    String pattern = "%" + search.trim().toLowerCase(Locale.ROOT) + "%";
    return (root, query, builder) ->
        builder.like(builder.lower(root.get("code").as(String.class)), pattern);
  }

  private void recordHistory(
      ServiceOrder order,
      ServiceOrderHistoryAction action,
      ServiceOrderStatus previousStatus,
      ServiceOrderStatus newStatus,
      String changeNote,
      String performedBy) {
    historyRepository.save(
        new ServiceOrderHistory(
            order.getId(), action, previousStatus, newStatus, changeNote, performedBy));
  }

  private String nullableTrim(String value) {
    return value == null || value.isBlank() ? null : value.trim();
  }

  private String producerName(Client producer) {
    return producer == null ? null : producer.getLegalName();
  }

  private String participantDocument(Client participant) {
    return participant == null
        ? null
        : participant.getDocumentType() + " " + participant.getDocumentNumber();
  }
}
