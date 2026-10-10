package com.labcore.platform.workorders;

import com.labcore.platform.catalog.CatalogService;
import com.labcore.platform.catalog.LaboratoryArea;
import com.labcore.platform.catalog.LaboratoryService;
import com.labcore.platform.catalog.SampleType;
import com.labcore.platform.common.exception.BusinessRuleViolationException;
import com.labcore.platform.common.exception.ResourceNotFoundException;
import com.labcore.platform.orders.DocumentCodeGenerator;
import com.labcore.platform.orders.RequestedTestStatus;
import com.labcore.platform.orders.ServiceOrder;
import com.labcore.platform.orders.ServiceOrderRepository;
import com.labcore.platform.orders.ServiceOrderSample;
import com.labcore.platform.orders.ServiceOrderSampleRepository;
import com.labcore.platform.orders.ServiceOrderSampleTest;
import com.labcore.platform.orders.ServiceOrderSampleTestRepository;
import com.labcore.platform.orders.ServiceOrderService;
import com.labcore.platform.orders.ServiceOrderStatus;
import com.labcore.platform.security.ApplicationUser;
import com.labcore.platform.security.ApplicationUserRepository;
import com.labcore.platform.security.RoleCode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class WorkOrderService {

  private final WorkOrderRepository workOrderRepository;
  private final WorkOrderSampleRepository workOrderSampleRepository;
  private final WorkOrderTestRepository workOrderTestRepository;
  private final WorkOrderHistoryRepository historyRepository;
  private final ServiceOrderRepository serviceOrderRepository;
  private final ServiceOrderService serviceOrderService;
  private final ServiceOrderSampleTestRepository requestedTestRepository;
  private final ServiceOrderSampleRepository sourceSampleRepository;
  private final TechnicalSampleRepository technicalSampleRepository;
  private final ApplicationUserRepository userRepository;
  private final CatalogService catalogService;
  private final SampleCodeGenerator sampleCodeGenerator;
  private final DocumentCodeGenerator documentCodeGenerator;
  private final WorkOrderDocumentService documentService;

  public WorkOrderService(
      WorkOrderRepository workOrderRepository,
      WorkOrderSampleRepository workOrderSampleRepository,
      WorkOrderTestRepository workOrderTestRepository,
      WorkOrderHistoryRepository historyRepository,
      ServiceOrderRepository serviceOrderRepository,
      ServiceOrderService serviceOrderService,
      ServiceOrderSampleTestRepository requestedTestRepository,
      ServiceOrderSampleRepository sourceSampleRepository,
      TechnicalSampleRepository technicalSampleRepository,
      ApplicationUserRepository userRepository,
      CatalogService catalogService,
      SampleCodeGenerator sampleCodeGenerator,
      DocumentCodeGenerator documentCodeGenerator,
      WorkOrderDocumentService documentService) {
    this.workOrderRepository = workOrderRepository;
    this.workOrderSampleRepository = workOrderSampleRepository;
    this.workOrderTestRepository = workOrderTestRepository;
    this.historyRepository = historyRepository;
    this.serviceOrderRepository = serviceOrderRepository;
    this.serviceOrderService = serviceOrderService;
    this.requestedTestRepository = requestedTestRepository;
    this.sourceSampleRepository = sourceSampleRepository;
    this.technicalSampleRepository = technicalSampleRepository;
    this.userRepository = userRepository;
    this.catalogService = catalogService;
    this.sampleCodeGenerator = sampleCodeGenerator;
    this.documentCodeGenerator = documentCodeGenerator;
    this.documentService = documentService;
  }

  public WorkOrderDtos.WorkOrderDetail create(
      WorkOrderDtos.CreateWorkOrderRequest request, String performedBy) {
    ensureCanManage(currentUser(performedBy));
    ServiceOrder sourceOrder = serviceOrderService.getRequired(request.serviceOrderId());
    ensureSourceOrderIsEligible(sourceOrder);
    validateDueDate(request.laboratoryDueAt(), sourceOrder.getPlannedDeliveryAt());
    LaboratoryArea area = catalogService.getArea(request.laboratoryAreaId());
    ApplicationUser responsible = requiredUser(request.responsibleId());
    validateResponsible(responsible, area.getId());

    List<AssignedSourceTest> selectedTests =
        validateAssignments(request, sourceOrder.getId(), area.getId());
    Map<UUID, UUID> classifications = validateClassifications(request, selectedTests);
    WorkOrder workOrder =
        workOrderRepository.save(
            new WorkOrder(
                documentCodeGenerator.next("OT"),
                sourceOrder.getId(),
                area.getId(),
                responsible.getId(),
                request.laboratoryDueAt()));
    Map<UUID, WorkOrderSample> samplesBySourceId =
        createTechnicalSamples(workOrder, selectedTests, classifications, performedBy);
    createTechnicalTests(selectedTests, samplesBySourceId);
    recordHistory(
        workOrder,
        WorkOrderHistoryAction.SAMPLES_CLASSIFIED,
        null,
        workOrder.getStatus(),
        classificationHistoryNote(selectedTests, samplesBySourceId),
        performedBy);
    recordHistory(
        workOrder,
        WorkOrderHistoryAction.CREATED,
        null,
        workOrder.getStatus(),
        "Orden de trabajo emitida con ensayos asignados.",
        performedBy);
    documentService.generateAtCreation(workOrder.getId(), performedBy);
    return getDetail(workOrder.getId(), performedBy);
  }

  @Transactional(readOnly = true)
  public Page<WorkOrderDtos.WorkOrderSummary> getPage(
      String search,
      WorkOrderStatus status,
      UUID laboratoryAreaId,
      Instant laboratoryDueFrom,
      Instant laboratoryDueTo,
      Instant createdFrom,
      Instant createdTo,
      Pageable pageable,
      String actorEmail) {
    ApplicationUser actor = currentUser(actorEmail);
    Specification<WorkOrder> specification =
        WorkOrderSpecifications.search(search)
            .and(WorkOrderSpecifications.status(status))
            .and(WorkOrderSpecifications.area(laboratoryAreaId))
            .and(WorkOrderSpecifications.dueFrom(laboratoryDueFrom))
            .and(WorkOrderSpecifications.dueTo(laboratoryDueTo))
            .and(WorkOrderSpecifications.createdFrom(createdFrom))
            .and(WorkOrderSpecifications.createdTo(createdTo));
    if (isAnalystRestricted(actor)) {
      Set<UUID> assignedIds = assignedWorkOrderIds(actor.getId());
      if (assignedIds.isEmpty()) {
        return Page.empty(pageable);
      }
      specification = specification.and(WorkOrderSpecifications.ids(assignedIds));
    }
    Page<WorkOrder> workOrders = workOrderRepository.findAll(specification, pageable);
    return toSummaryPage(workOrders);
  }

  @Transactional(readOnly = true)
  public WorkOrderDtos.WorkOrderDetail getDetail(UUID workOrderId, String actorEmail) {
    ApplicationUser actor = currentUser(actorEmail);
    WorkOrder workOrder = getRequired(workOrderId);
    List<WorkOrderSample> samples = workOrderSampleRepository.findByWorkOrderId(workOrderId);
    List<WorkOrderTest> tests = getTests(samples);
    assertCanView(actor, tests);
    return toDetail(
        workOrder,
        samples,
        tests,
        isAnalystRestricted(actor) ? actor.getId() : null,
        hasRole(actor, RoleCode.ADMIN) || hasRole(actor, RoleCode.TECHNICAL_MANAGER));
  }

  @Transactional(readOnly = true)
  public List<WorkOrderDtos.EligibleServiceOrder> getEligibleServiceOrders(String actorEmail) {
    ensureCanManage(currentUser(actorEmail));
    return serviceOrderRepository
        .findByStatusOrderByPlannedDeliveryAtAsc(ServiceOrderStatus.IN_PROGRESS)
        .stream()
        .map(
            order -> {
              List<SourceTestContext> tests = pendingSourceTests(order.getId());
              long sampleCount =
                  tests.stream().map(item -> item.sample().getId()).distinct().count();
              return new WorkOrderDtos.EligibleServiceOrder(
                  order.getId(),
                  order.getCode(),
                  order.getPlannedDeliveryAt(),
                  tests.size(),
                  (int) sampleCount);
            })
        .filter(order -> order.pendingTestCount() > 0)
        .toList();
  }

  @Transactional(readOnly = true)
  public WorkOrderDtos.EligibleServiceOrderDetail getEligibleServiceOrder(
      UUID serviceOrderId, String actorEmail) {
    ensureCanManage(currentUser(actorEmail));
    ServiceOrder sourceOrder = serviceOrderService.getRequired(serviceOrderId);
    ensureSourceOrderIsEligible(sourceOrder);
    List<SourceTestContext> pendingTests = pendingSourceTests(serviceOrderId);
    if (pendingTests.isEmpty()) {
      throw new BusinessRuleViolationException(
          "La orden de servicio ya no tiene ensayos disponibles para una orden de trabajo.");
    }

    Map<UUID, LaboratoryArea> areasById = new HashMap<>();
    Map<UUID, TechnicalSample> technicalSamplesBySourceId =
        technicalSampleRepository
            .findByServiceOrderSampleIdIn(
                pendingTests.stream().map(item -> item.sample().getId()).distinct().toList())
            .stream()
            .collect(
                Collectors.toMap(TechnicalSample::getServiceOrderSampleId, Function.identity()));
    Map<UUID, List<SourceTestContext>> testsBySample =
        pendingTests.stream().collect(Collectors.groupingBy(item -> item.sample().getId()));
    List<WorkOrderDtos.EligibleSample> samples =
        testsBySample.values().stream()
            .map(
                sampleTests -> {
                  ServiceOrderSample sample = sampleTests.getFirst().sample();
                  SampleType declaredType = catalogService.getSampleType(sample.getSampleTypeId());
                  TechnicalSample technicalSample = technicalSamplesBySourceId.get(sample.getId());
                  return new WorkOrderDtos.EligibleSample(
                      sample.getId(),
                      sample.getCommercialSequence(),
                      declaredType.getId(),
                      declaredType.getName(),
                      technicalSample == null ? null : technicalSample.getTechnicalSampleTypeId(),
                      technicalSample == null
                          ? null
                          : catalogService
                              .getSampleType(technicalSample.getTechnicalSampleTypeId())
                              .getName(),
                      technicalSample == null ? null : technicalSample.getLaboratoryCode(),
                      sampleTests.stream().map(test -> toEligibleTest(test, areasById)).toList());
                })
            .sorted(
                java.util.Comparator.comparingInt(WorkOrderDtos.EligibleSample::commercialSequence))
            .toList();
    Map<UUID, Long> pendingCountByArea =
        samples.stream()
            .flatMap(sample -> sample.pendingTests().stream())
            .collect(
                Collectors.groupingBy(
                    WorkOrderDtos.EligibleTest::laboratoryAreaId, Collectors.counting()));
    List<WorkOrderDtos.EligibleLaboratoryArea> areas =
        pendingCountByArea.entrySet().stream()
            .map(
                entry -> {
                  LaboratoryArea area = areasById.get(entry.getKey());
                  return new WorkOrderDtos.EligibleLaboratoryArea(
                      area.getId(), area.getName(), Math.toIntExact(entry.getValue()));
                })
            .sorted(java.util.Comparator.comparing(WorkOrderDtos.EligibleLaboratoryArea::name))
            .toList();
    return new WorkOrderDtos.EligibleServiceOrderDetail(
        sourceOrder.getId(),
        sourceOrder.getCode(),
        sourceOrder.getPlannedDeliveryAt(),
        areas,
        samples);
  }

  public void cancel(
      UUID workOrderId, WorkOrderDtos.CancelWorkOrderRequest request, String performedBy) {
    ensureCanManage(currentUser(performedBy));
    WorkOrder workOrder = getRequired(workOrderId);
    if (workOrder.getStatus() == WorkOrderStatus.CANCELLED) {
      throw new BusinessRuleViolationException("La orden de trabajo ya fue anulada.");
    }
    if (workOrder.getStatus() == WorkOrderStatus.CLOSED) {
      throw new BusinessRuleViolationException("No se puede anular una orden de trabajo cerrada.");
    }
    List<WorkOrderTest> tests = getTests(workOrderSampleRepository.findByWorkOrderId(workOrderId));
    if (tests.stream().anyMatch(test -> test.getStatus() != WorkOrderTestStatus.PENDING)) {
      throw new BusinessRuleViolationException(
          "No se puede anular una orden de trabajo que ya tiene resultados registrados.");
    }
    WorkOrderStatus previousStatus = workOrder.getStatus();
    workOrder.cancel(trimToNull(request.reason()));
    requestedTestRepository
        .findAllById(tests.stream().map(WorkOrderTest::getSourceSampleTestId).toList())
        .forEach(ServiceOrderSampleTest::markPending);
    recordHistory(
        workOrder,
        WorkOrderHistoryAction.CANCELLED,
        previousStatus,
        workOrder.getStatus(),
        workOrder.getCancellationReason(),
        performedBy);
  }

  public void close(UUID workOrderId, String performedBy) {
    ensureCanManage(currentUser(performedBy));
    WorkOrder workOrder = getRequired(workOrderId);
    if (workOrder.getStatus() != WorkOrderStatus.VALIDATED) {
      throw new BusinessRuleViolationException(
          "Solo se puede cerrar una orden de trabajo con todos sus resultados validados.");
    }
    WorkOrderStatus previousStatus = workOrder.getStatus();
    workOrder.close();
    recordHistory(
        workOrder,
        WorkOrderHistoryAction.CLOSED,
        previousStatus,
        workOrder.getStatus(),
        "Orden técnica cerrada.",
        performedBy);
  }

  @Transactional(readOnly = true)
  public Page<WorkOrderDtos.WorkOrderHistoryEntry> getHistory(
      UUID workOrderId, Pageable pageable, String actorEmail) {
    WorkOrder workOrder = getRequired(workOrderId);
    assertCanView(
        currentUser(actorEmail),
        getTests(workOrderSampleRepository.findByWorkOrderId(workOrderId)));
    return historyRepository
        .findByWorkOrderIdOrderByOccurredAtDesc(workOrder.getId(), pageable)
        .map(WorkOrderDtos.WorkOrderHistoryEntry::from);
  }

  public WorkOrderDtos.WorkOrderDocumentResponse getDocument(UUID workOrderId, String actorEmail) {
    WorkOrder workOrder = getRequired(workOrderId);
    assertCanView(
        currentUser(actorEmail),
        getTests(workOrderSampleRepository.findByWorkOrderId(workOrderId)));
    return documentService.getDocument(workOrder.getId(), actorEmail);
  }

  @Transactional(readOnly = true)
  public WorkOrder getRequired(UUID id) {
    return workOrderRepository
        .findById(id)
        .orElseThrow(() -> new ResourceNotFoundException("la orden de trabajo", id));
  }

  @Transactional(readOnly = true)
  public WorkOrderTest getRequiredTest(UUID id) {
    return workOrderTestRepository
        .findById(id)
        .orElseThrow(() -> new ResourceNotFoundException("el ensayo de trabajo", id));
  }

  public void synchronizeStatus(
      UUID workOrderId,
      String performedBy,
      WorkOrderHistoryAction action,
      String resultDescription) {
    WorkOrder workOrder = getRequired(workOrderId);
    if (workOrder.getStatus() == WorkOrderStatus.CANCELLED
        || workOrder.getStatus() == WorkOrderStatus.CLOSED) {
      return;
    }
    List<WorkOrderTest> tests = getTests(workOrderSampleRepository.findByWorkOrderId(workOrderId));
    WorkOrderStatus nextStatus = determineStatus(tests);
    WorkOrderStatus previousStatus = workOrder.getStatus();
    if (nextStatus != previousStatus) {
      updateOperationalStatus(workOrder, nextStatus);
    }
    long recordedResults =
        tests.stream().filter(test -> test.getStatus() != WorkOrderTestStatus.PENDING).count();
    long validatedResults =
        tests.stream().filter(test -> test.getStatus() == WorkOrderTestStatus.VALIDATED).count();
    String progress =
        "Avance: %d de %d resultado(s) registrado(s) y %d de %d validado(s)."
            .formatted(recordedResults, tests.size(), validatedResults, tests.size());
    recordHistory(
        workOrder,
        action,
        previousStatus,
        nextStatus,
        resultDescription + " " + progress,
        performedBy);
  }

  private Set<UUID> assignedWorkOrderIds(UUID analystId) {
    List<WorkOrderTest> assignedTests = workOrderTestRepository.findByAssignedAnalystId(analystId);
    if (assignedTests.isEmpty()) {
      return Set.of();
    }
    List<WorkOrderSample> samples = new ArrayList<>();
    workOrderSampleRepository
        .findAllById(
            assignedTests.stream().map(WorkOrderTest::getWorkOrderSampleId).distinct().toList())
        .forEach(samples::add);
    return samples.stream().map(WorkOrderSample::getWorkOrderId).collect(Collectors.toSet());
  }

  private Page<WorkOrderDtos.WorkOrderSummary> toSummaryPage(Page<WorkOrder> workOrders) {
    Map<UUID, ApplicationUser> responsibleById =
        userRepository
            .findAllById(workOrders.stream().map(WorkOrder::getResponsibleId).distinct().toList())
            .stream()
            .collect(Collectors.toMap(ApplicationUser::getId, Function.identity()));
    Map<UUID, LaboratoryArea> areaById =
        workOrders.stream()
            .map(WorkOrder::getLaboratoryAreaId)
            .distinct()
            .map(catalogService::getArea)
            .collect(Collectors.toMap(LaboratoryArea::getId, Function.identity()));
    Map<UUID, ServiceOrder> serviceOrderById =
        serviceOrderRepository
            .findAllById(workOrders.stream().map(WorkOrder::getServiceOrderId).distinct().toList())
            .stream()
            .collect(Collectors.toMap(ServiceOrder::getId, Function.identity()));
    return workOrders.map(
        workOrder ->
            new WorkOrderDtos.WorkOrderSummary(
                workOrder.getId(),
                workOrder.getCode(),
                workOrder.getServiceOrderId(),
                serviceOrderById.get(workOrder.getServiceOrderId()).getCode(),
                workOrder.getLaboratoryAreaId(),
                areaById.get(workOrder.getLaboratoryAreaId()).getName(),
                workOrder.getResponsibleId(),
                responsibleById.get(workOrder.getResponsibleId()).getFullName(),
                workOrder.getLaboratoryDueAt(),
                workOrder.getStatus(),
                workOrder.getCreatedAt(),
                workOrderSampleRepository.findByWorkOrderId(workOrder.getId()).size()));
  }

  private WorkOrderDtos.WorkOrderDetail toDetail(
      WorkOrder workOrder,
      List<WorkOrderSample> samples,
      List<WorkOrderTest> tests,
      UUID analystId,
      boolean exposeCorrectionReason) {
    List<WorkOrderTest> visibleTests =
        analystId == null
            ? tests
            : tests.stream().filter(test -> test.getAssignedAnalystId().equals(analystId)).toList();
    Map<UUID, List<WorkOrderTest>> testsBySample =
        visibleTests.stream().collect(Collectors.groupingBy(WorkOrderTest::getWorkOrderSampleId));
    Map<UUID, ServiceOrderSampleTest> sourceTests =
        requestedTestRepository
            .findAllById(visibleTests.stream().map(WorkOrderTest::getSourceSampleTestId).toList())
            .stream()
            .collect(Collectors.toMap(ServiceOrderSampleTest::getId, Function.identity()));
    Map<UUID, ApplicationUser> analysts =
        userRepository
            .findAllById(
                visibleTests.stream().map(WorkOrderTest::getAssignedAnalystId).distinct().toList())
            .stream()
            .collect(Collectors.toMap(ApplicationUser::getId, Function.identity()));
    ApplicationUser responsible = requiredUser(workOrder.getResponsibleId());
    ServiceOrder serviceOrder = serviceOrderService.getRequired(workOrder.getServiceOrderId());
    String serviceOrderCode = serviceOrder.getCode();
    List<WorkOrderDtos.WorkOrderSampleResponse> responseSamples =
        samples.stream()
            .filter(sample -> testsBySample.containsKey(sample.getId()))
            .map(
                sample ->
                    new WorkOrderDtos.WorkOrderSampleResponse(
                        sample.getId(),
                        sample.getTechnicalSampleId(),
                        sample.getLaboratoryCode(),
                        sample.getSampleTypeId(),
                        catalogService.getSampleType(sample.getSampleTypeId()).getName(),
                        testsBySample.get(sample.getId()).stream()
                            .map(
                                test -> {
                                  ServiceOrderSampleTest sourceTest =
                                      sourceTests.get(test.getSourceSampleTestId());
                                  ApplicationUser analyst =
                                      analysts.get(test.getAssignedAnalystId());
                                  return new WorkOrderDtos.WorkOrderTestResponse(
                                      test.getId(),
                                      test.getSourceSampleTestId(),
                                      sourceTest.getTestDefinitionId(),
                                      catalogService
                                          .getTest(sourceTest.getTestDefinitionId())
                                          .getName(),
                                      test.getTestMethodId(),
                                      catalogService.getMethod(test.getTestMethodId()).getName(),
                                      test.getAssignedAnalystId(),
                                      analyst.getFullName(),
                                      test.getStatus());
                                })
                            .toList()))
            .toList();
    return new WorkOrderDtos.WorkOrderDetail(
        workOrder.getId(),
        workOrder.getCode(),
        workOrder.getServiceOrderId(),
        serviceOrderCode,
        workOrder.getLaboratoryAreaId(),
        catalogService.getArea(workOrder.getLaboratoryAreaId()).getName(),
        workOrder.getResponsibleId(),
        responsible.getFullName(),
        workOrder.getLaboratoryDueAt(),
        workOrder.getStatus(),
        serviceOrder.getStatus(),
        exposeCorrectionReason ? serviceOrder.getCorrectionReason() : null,
        exposeCorrectionReason ? serviceOrder.getCorrectionChangeNote() : null,
        exposeCorrectionReason ? serviceOrder.getCorrectionImpactSummary() : null,
        exposeCorrectionReason && serviceOrder.isCorrectionRequiresTechnicalWork(),
        workOrder.getCancelledAt(),
        workOrder.getCancellationReason(),
        workOrder.getClosedAt(),
        responseSamples);
  }

  private List<AssignedSourceTest> validateAssignments(
      WorkOrderDtos.CreateWorkOrderRequest request, UUID serviceOrderId, UUID laboratoryAreaId) {
    List<UUID> requestedIds =
        request.assignments().stream()
            .map(WorkOrderDtos.AnalystAssignmentRequest::sourceSampleTestId)
            .sorted()
            .toList();
    if (new HashSet<>(requestedIds).size() != requestedIds.size()) {
      throw new BusinessRuleViolationException(
          "Un ensayo no puede asignarse más de una vez en la misma orden de trabajo.");
    }
    Map<UUID, ServiceOrderSampleTest> lockedTests =
        requestedTestRepository.findAllByIdForUpdate(requestedIds).stream()
            .collect(Collectors.toMap(ServiceOrderSampleTest::getId, Function.identity()));
    List<UUID> sourceSampleIds =
        lockedTests.values().stream()
            .map(ServiceOrderSampleTest::getServiceOrderSampleId)
            .distinct()
            .sorted()
            .toList();
    Map<UUID, ServiceOrderSample> lockedSamples =
        sourceSampleRepository.findAllByIdForUpdate(sourceSampleIds).stream()
            .collect(Collectors.toMap(ServiceOrderSample::getId, Function.identity()));
    List<AssignedSourceTest> selectedTests = new ArrayList<>();
    for (WorkOrderDtos.AnalystAssignmentRequest assignment : request.assignments()) {
      ServiceOrderSampleTest lockedTest = lockedTests.get(assignment.sourceSampleTestId());
      ServiceOrderSample sourceSample =
          lockedTest == null ? null : lockedSamples.get(lockedTest.getServiceOrderSampleId());
      if (lockedTest == null
          || sourceSample == null
          || !sourceSample.getServiceOrderId().equals(serviceOrderId)
          || lockedTest.getStatus() != RequestedTestStatus.PENDING) {
        throw new BusinessRuleViolationException(
            "El ensayo seleccionado no está disponible para emitir esta orden de trabajo.");
      }
      LaboratoryService service = catalogService.getService(lockedTest.getLaboratoryServiceId());
      if (!service.getLaboratoryAreaId().equals(laboratoryAreaId)) {
        throw new BusinessRuleViolationException(
            "Todos los ensayos de una orden de trabajo deben pertenecer al área técnica seleccionada.");
      }
      validateAnalyst(requiredUser(assignment.analystId()), laboratoryAreaId);
      selectedTests.add(
          new AssignedSourceTest(
              new SourceTestContext(sourceSample, lockedTest), assignment.analystId()));
    }
    return selectedTests;
  }

  private Map<UUID, WorkOrderSample> createTechnicalSamples(
      WorkOrder workOrder,
      List<AssignedSourceTest> selectedTests,
      Map<UUID, UUID> classifications,
      String classifiedBy) {
    Map<UUID, WorkOrderSample> samplesBySourceId = new HashMap<>();
    for (AssignedSourceTest selectedTest : selectedTests) {
      ServiceOrderSample sourceSample = selectedTest.sourceTest().sample();
      if (samplesBySourceId.containsKey(sourceSample.getId())) {
        continue;
      }
      TechnicalSample technicalIdentity =
          technicalSampleRepository
              .findByServiceOrderSampleId(sourceSample.getId())
              .orElseGet(
                  () ->
                      createTechnicalSample(
                          sourceSample, classifications.get(sourceSample.getId()), classifiedBy));
      WorkOrderSample workOrderSample =
          workOrderSampleRepository.save(new WorkOrderSample(workOrder.getId(), technicalIdentity));
      samplesBySourceId.put(sourceSample.getId(), workOrderSample);
    }
    return samplesBySourceId;
  }

  private Map<UUID, UUID> validateClassifications(
      WorkOrderDtos.CreateWorkOrderRequest request, List<AssignedSourceTest> selectedTests) {
    Set<UUID> selectedSampleIds =
        selectedTests.stream()
            .map(selected -> selected.sourceTest().sample().getId())
            .collect(Collectors.toSet());
    Map<UUID, UUID> classifications = new HashMap<>();
    for (WorkOrderDtos.SampleClassificationRequest classification :
        request.sampleClassifications()) {
      if (!selectedSampleIds.contains(classification.sourceSampleId())) {
        throw new BusinessRuleViolationException(
            "Solo se puede clasificar una muestra incluida en la orden de trabajo.");
      }
      if (classifications.put(
              classification.sourceSampleId(), classification.technicalSampleTypeId())
          != null) {
        throw new BusinessRuleViolationException(
            "Cada muestra debe tener una única clasificación técnica.");
      }
    }
    if (!classifications.keySet().equals(selectedSampleIds)) {
      throw new BusinessRuleViolationException(
          "Debes confirmar la clasificación técnica de cada muestra seleccionada.");
    }

    Map<UUID, TechnicalSample> existingIdentities =
        technicalSampleRepository.findByServiceOrderSampleIdIn(selectedSampleIds).stream()
            .collect(
                Collectors.toMap(TechnicalSample::getServiceOrderSampleId, Function.identity()));
    classifications.forEach(
        (sampleId, technicalTypeId) -> {
          TechnicalSample existing = existingIdentities.get(sampleId);
          if (existing != null) {
            if (!existing.getTechnicalSampleTypeId().equals(technicalTypeId)) {
              throw new BusinessRuleViolationException(
                  "La muestra ya tiene una clasificación técnica y un código interno definitivos.");
            }
            return;
          }
          SampleType sampleType = catalogService.getSampleType(technicalTypeId);
          if (!sampleType.isActive()) {
            throw new BusinessRuleViolationException(
                "El tipo de muestra técnico seleccionado se encuentra desactivado.");
          }
        });
    return classifications;
  }

  private TechnicalSample createTechnicalSample(
      ServiceOrderSample sourceSample, UUID technicalSampleTypeId, String classifiedBy) {
    SampleType technicalSampleType = catalogService.getSampleType(technicalSampleTypeId);
    SampleCodeGenerator.GeneratedSampleCode generatedCode =
        sampleCodeGenerator.next(technicalSampleType);
    return technicalSampleRepository.save(
        new TechnicalSample(
            sourceSample.getId(),
            sourceSample.getSampleTypeId(),
            technicalSampleTypeId,
            generatedCode.value(),
            generatedCode.sequence(),
            classifiedBy));
  }

  private String classificationHistoryNote(
      List<AssignedSourceTest> selectedTests, Map<UUID, WorkOrderSample> samplesBySourceId) {
    Map<UUID, ServiceOrderSample> sourceSamples =
        selectedTests.stream()
            .map(selected -> selected.sourceTest().sample())
            .collect(
                Collectors.toMap(
                    ServiceOrderSample::getId, Function.identity(), (first, duplicate) -> first));
    String classifications =
        samplesBySourceId.entrySet().stream()
            .sorted(
                java.util.Comparator.comparingInt(
                    entry -> sourceSamples.get(entry.getKey()).getCommercialSequence()))
            .map(
                entry -> {
                  ServiceOrderSample sourceSample = sourceSamples.get(entry.getKey());
                  WorkOrderSample technicalSample = entry.getValue();
                  String declaredType =
                      catalogService.getSampleType(sourceSample.getSampleTypeId()).getName();
                  String technicalType =
                      catalogService.getSampleType(technicalSample.getSampleTypeId()).getName();
                  return "Muestra comercial %d: %s → %s; código %s"
                      .formatted(
                          sourceSample.getCommercialSequence(),
                          declaredType,
                          technicalType,
                          technicalSample.getLaboratoryCode());
                })
            .collect(Collectors.joining(". "));
    return "Clasificación técnica confirmada. " + classifications + ".";
  }

  private void createTechnicalTests(
      List<AssignedSourceTest> selectedTests, Map<UUID, WorkOrderSample> samplesBySourceId) {
    for (AssignedSourceTest selectedTest : selectedTests) {
      ServiceOrderSampleTest sourceTest = selectedTest.sourceTest().test();
      WorkOrderSample technicalSample =
          samplesBySourceId.get(selectedTest.sourceTest().sample().getId());
      workOrderTestRepository.save(
          new WorkOrderTest(
              technicalSample.getId(),
              sourceTest.getId(),
              selectedTest.analystId(),
              sourceTest.getTestMethodId()));
      sourceTest.markAssigned();
    }
  }

  private List<WorkOrderTest> getTests(List<WorkOrderSample> samples) {
    if (samples.isEmpty()) {
      return List.of();
    }
    return workOrderTestRepository.findByWorkOrderSampleIdIn(
        samples.stream().map(WorkOrderSample::getId).toList());
  }

  private List<SourceTestContext> pendingSourceTests(UUID serviceOrderId) {
    List<ServiceOrderSample> samples = serviceOrderService.getSamples(serviceOrderId);
    Map<UUID, ServiceOrderSample> samplesById =
        samples.stream().collect(Collectors.toMap(ServiceOrderSample::getId, Function.identity()));
    return serviceOrderService.getRequestedTests(samplesById.keySet()).stream()
        .filter(test -> test.getStatus() == RequestedTestStatus.PENDING)
        .map(test -> new SourceTestContext(samplesById.get(test.getServiceOrderSampleId()), test))
        .toList();
  }

  private WorkOrderDtos.EligibleTest toEligibleTest(
      SourceTestContext context, Map<UUID, LaboratoryArea> areasById) {
    LaboratoryService service = catalogService.getService(context.test().getLaboratoryServiceId());
    LaboratoryArea area =
        areasById.computeIfAbsent(service.getLaboratoryAreaId(), catalogService::getArea);
    return new WorkOrderDtos.EligibleTest(
        context.test().getId(),
        area.getId(),
        area.getName(),
        service.getName(),
        context.test().getTestDefinitionId(),
        catalogService.getTest(context.test().getTestDefinitionId()).getName(),
        context.test().getTestMethodId(),
        catalogService.getMethod(context.test().getTestMethodId()).getName());
  }

  private WorkOrderStatus determineStatus(List<WorkOrderTest> tests) {
    if (tests.isEmpty()
        || tests.stream().allMatch(test -> test.getStatus() == WorkOrderTestStatus.PENDING)) {
      return WorkOrderStatus.PENDING;
    }
    if (tests.stream()
        .allMatch(
            test ->
                test.getStatus() == WorkOrderTestStatus.VALIDATED
                    || test.getStatus() == WorkOrderTestStatus.INVALIDATED)) {
      return WorkOrderStatus.VALIDATED;
    }
    if (tests.stream().allMatch(test -> test.getStatus() != WorkOrderTestStatus.PENDING)) {
      return WorkOrderStatus.RESULTS_RECORDED;
    }
    return WorkOrderStatus.IN_PROGRESS;
  }

  private void updateOperationalStatus(WorkOrder workOrder, WorkOrderStatus status) {
    switch (status) {
      case IN_PROGRESS -> workOrder.markInProgress();
      case RESULTS_RECORDED -> workOrder.markResultsRecorded();
      case VALIDATED -> workOrder.markValidated();
      default -> throw new IllegalArgumentException("Estado operativo no permitido: " + status);
    }
  }

  private void validateDueDate(Instant laboratoryDueAt, Instant plannedDeliveryAt) {
    if (laboratoryDueAt.isAfter(plannedDeliveryAt)) {
      throw new BusinessRuleViolationException(
          "La fecha interna de laboratorio no puede ser posterior a la entrega prometida al cliente.");
    }
  }

  private void ensureSourceOrderIsEligible(ServiceOrder sourceOrder) {
    if (sourceOrder.getStatus() != ServiceOrderStatus.IN_PROGRESS) {
      throw new BusinessRuleViolationException(
          "Solo se puede emitir una orden de trabajo desde una orden de servicio en proceso.");
    }
  }

  private void validateResponsible(ApplicationUser user, UUID laboratoryAreaId) {
    if (!user.isActive()
        || (!hasRole(user, RoleCode.TECHNICAL_MANAGER) && !hasRole(user, RoleCode.ADMIN))) {
      throw new BusinessRuleViolationException(
          "El responsable debe ser un usuario activo con rol técnico o administrador.");
    }
    validateUserArea(user, laboratoryAreaId, "responsable técnico");
  }

  private void validateAnalyst(ApplicationUser user, UUID laboratoryAreaId) {
    if (!user.isActive() || (!hasRole(user, RoleCode.ANALYST) && !hasRole(user, RoleCode.ADMIN))) {
      throw new BusinessRuleViolationException(
          "Cada analista debe ser un usuario activo con el rol de analista.");
    }
    validateUserArea(user, laboratoryAreaId, "analista");
  }

  private void validateUserArea(
      ApplicationUser user, UUID laboratoryAreaId, String assignmentLabel) {
    if (!hasRole(user, RoleCode.ADMIN) && !laboratoryAreaId.equals(user.getLaboratoryAreaId())) {
      throw new BusinessRuleViolationException(
          "El %s debe pertenecer al área técnica seleccionada.".formatted(assignmentLabel));
    }
  }

  private void assertCanView(ApplicationUser actor, List<WorkOrderTest> tests) {
    if (hasElevatedTechnicalAccess(actor)) {
      return;
    }
    boolean isAssigned =
        tests.stream().anyMatch(test -> test.getAssignedAnalystId().equals(actor.getId()));
    if (!isAssigned) {
      throw new BusinessRuleViolationException(
          "Solo puedes consultar órdenes de trabajo con ensayos asignados a tu usuario.");
    }
  }

  private boolean isAnalystRestricted(ApplicationUser user) {
    return hasRole(user, RoleCode.ANALYST) && !hasElevatedTechnicalAccess(user);
  }

  private boolean hasElevatedTechnicalAccess(ApplicationUser user) {
    return hasRole(user, RoleCode.ADMIN)
        || hasRole(user, RoleCode.TECHNICAL_MANAGER)
        || hasRole(user, RoleCode.VALIDATOR);
  }

  private void ensureCanManage(ApplicationUser user) {
    if (!hasRole(user, RoleCode.ADMIN) && !hasRole(user, RoleCode.TECHNICAL_MANAGER)) {
      throw new BusinessRuleViolationException(
          "Solo administración o gestión técnica puede administrar órdenes de trabajo.");
    }
  }

  private ApplicationUser currentUser(String email) {
    ApplicationUser user =
        userRepository
            .findByEmailIgnoreCase(email)
            .orElseThrow(
                () -> new ResourceNotFoundException("el usuario autenticado", UUID.randomUUID()));
    if (!user.isActive()) {
      throw new BusinessRuleViolationException("El usuario autenticado se encuentra desactivado.");
    }
    return user;
  }

  private ApplicationUser requiredUser(UUID id) {
    return userRepository
        .findById(id)
        .orElseThrow(() -> new ResourceNotFoundException("el usuario", id));
  }

  private boolean hasRole(ApplicationUser user, RoleCode code) {
    return user.getRoles().stream().anyMatch(role -> role.getCode() == code);
  }

  private void recordHistory(
      WorkOrder workOrder,
      WorkOrderHistoryAction action,
      WorkOrderStatus previousStatus,
      WorkOrderStatus newStatus,
      String note,
      String performedBy) {
    historyRepository.save(
        new WorkOrderHistory(
            workOrder.getId(), action, previousStatus, newStatus, note, performedBy));
  }

  private String trimToNull(String value) {
    return value == null || value.isBlank() ? null : value.trim();
  }

  private record SourceTestContext(ServiceOrderSample sample, ServiceOrderSampleTest test) {}

  private record AssignedSourceTest(SourceTestContext sourceTest, UUID analystId) {}
}
