package com.labcore.platform.workorders;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.labcore.platform.catalog.CatalogService;
import com.labcore.platform.common.exception.ResourceNotFoundException;
import com.labcore.platform.orders.ServiceOrder;
import com.labcore.platform.orders.ServiceOrderRepository;
import com.labcore.platform.orders.ServiceOrderSampleTest;
import com.labcore.platform.orders.ServiceOrderSampleTestRepository;
import com.labcore.platform.organization.OrganizationProfileService;
import com.labcore.platform.security.ApplicationUser;
import com.labcore.platform.security.ApplicationUserRepository;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class WorkOrderDocumentService {

  private final WorkOrderRepository workOrderRepository;
  private final WorkOrderSampleRepository sampleRepository;
  private final WorkOrderTestRepository testRepository;
  private final ServiceOrderSampleTestRepository sourceTestRepository;
  private final ServiceOrderRepository serviceOrderRepository;
  private final WorkOrderDocumentRepository documentRepository;
  private final ApplicationUserRepository userRepository;
  private final CatalogService catalogService;
  private final OrganizationProfileService organizationService;
  private final ObjectMapper objectMapper;

  public WorkOrderDocumentService(
      WorkOrderRepository workOrderRepository,
      WorkOrderSampleRepository sampleRepository,
      WorkOrderTestRepository testRepository,
      ServiceOrderSampleTestRepository sourceTestRepository,
      ServiceOrderRepository serviceOrderRepository,
      WorkOrderDocumentRepository documentRepository,
      ApplicationUserRepository userRepository,
      CatalogService catalogService,
      OrganizationProfileService organizationService,
      ObjectMapper objectMapper) {
    this.workOrderRepository = workOrderRepository;
    this.sampleRepository = sampleRepository;
    this.testRepository = testRepository;
    this.sourceTestRepository = sourceTestRepository;
    this.serviceOrderRepository = serviceOrderRepository;
    this.documentRepository = documentRepository;
    this.userRepository = userRepository;
    this.catalogService = catalogService;
    this.organizationService = organizationService;
    this.objectMapper = objectMapper;
  }

  public WorkOrderDtos.WorkOrderDocumentResponse generateAtCreation(
      UUID workOrderId, String generatedBy) {
    WorkOrder order = getRequired(workOrderId);
    return documentRepository
        .findFirstByWorkOrderIdOrderByVersionDesc(workOrderId)
        .map(this::readDocument)
        .orElseGet(() -> createDocument(order, generatedBy));
  }

  public WorkOrderDtos.WorkOrderDocumentResponse getDocument(UUID workOrderId, String generatedBy) {
    return documentRepository
        .findFirstByWorkOrderIdOrderByVersionDesc(workOrderId)
        .map(this::readDocument)
        .orElseGet(() -> createDocument(getRequired(workOrderId), generatedBy));
  }

  private WorkOrder getRequired(UUID workOrderId) {
    return workOrderRepository
        .findById(workOrderId)
        .orElseThrow(() -> new ResourceNotFoundException("la orden de trabajo", workOrderId));
  }

  private WorkOrderDtos.WorkOrderDocumentResponse createDocument(
      WorkOrder order, String generatedBy) {
    WorkOrderDtos.WorkOrderDocumentResponse snapshot = buildSnapshot(order);
    WorkOrderDocument document =
        documentRepository.save(
            new WorkOrderDocument(
                order.getId(), nextVersion(order.getId()), serialize(snapshot), generatedBy));
    return withDocumentMetadata(snapshot, document);
  }

  public WorkOrderDtos.WorkOrderDocumentResponse regenerateAfterCorrection(
      UUID workOrderId, String generatedBy) {
    return createDocument(getRequired(workOrderId), generatedBy);
  }

  private int nextVersion(UUID workOrderId) {
    return documentRepository
        .findFirstByWorkOrderIdOrderByVersionDesc(workOrderId)
        .map(document -> document.getVersion() + 1)
        .orElse(1);
  }

  private WorkOrderDtos.WorkOrderDocumentResponse buildSnapshot(WorkOrder order) {
    List<WorkOrderSample> samples = sampleRepository.findByWorkOrderId(order.getId());
    List<WorkOrderTest> tests =
        testRepository
            .findByWorkOrderSampleIdIn(samples.stream().map(WorkOrderSample::getId).toList())
            .stream()
            .filter(test -> test.getStatus() != WorkOrderTestStatus.INVALIDATED)
            .toList();
    Map<UUID, List<WorkOrderTest>> testsBySample =
        tests.stream().collect(Collectors.groupingBy(WorkOrderTest::getWorkOrderSampleId));
    Map<UUID, ServiceOrderSampleTest> sourceTests =
        sourceTestRepository
            .findAllById(tests.stream().map(WorkOrderTest::getSourceSampleTestId).toList())
            .stream()
            .collect(Collectors.toMap(ServiceOrderSampleTest::getId, Function.identity()));
    Map<UUID, ApplicationUser> analysts =
        userRepository
            .findAllById(
                tests.stream().map(WorkOrderTest::getAssignedAnalystId).distinct().toList())
            .stream()
            .collect(Collectors.toMap(ApplicationUser::getId, Function.identity()));
    ApplicationUser responsible = requiredUser(order.getResponsibleId());
    ServiceOrder sourceOrder =
        serviceOrderRepository
            .findById(order.getServiceOrderId())
            .orElseThrow(
                () ->
                    new ResourceNotFoundException(
                        "la orden de servicio de la orden de trabajo", order.getServiceOrderId()));

    return new WorkOrderDtos.WorkOrderDocumentResponse(
        null,
        0,
        Instant.now(),
        organizationService.documentIdentity(),
        order.getCode(),
        sourceOrder.getCode(),
        catalogService.getArea(order.getLaboratoryAreaId()).getName(),
        responsible.getFullName(),
        order.getLaboratoryDueAt(),
        samples.stream()
            .map(
                sample ->
                    new WorkOrderDtos.DocumentSample(
                        sample.getLaboratoryCode(),
                        catalogService.getSampleType(sample.getSampleTypeId()).getName(),
                        testsBySample.getOrDefault(sample.getId(), List.of()).stream()
                            .map(
                                test -> {
                                  ServiceOrderSampleTest sourceTest =
                                      sourceTests.get(test.getSourceSampleTestId());
                                  return new WorkOrderDtos.DocumentTest(
                                      catalogService
                                          .getTest(sourceTest.getTestDefinitionId())
                                          .getName(),
                                      catalogService.getMethod(test.getTestMethodId()).getName(),
                                      analysts.get(test.getAssignedAnalystId()).getFullName());
                                })
                            .toList()))
            .toList());
  }

  private ApplicationUser requiredUser(UUID userId) {
    return userRepository
        .findById(userId)
        .orElseThrow(() -> new ResourceNotFoundException("el usuario asignado", userId));
  }

  private String serialize(WorkOrderDtos.WorkOrderDocumentResponse snapshot) {
    try {
      return objectMapper.writeValueAsString(snapshot);
    } catch (JsonProcessingException exception) {
      throw new IllegalStateException(
          "No fue posible almacenar el documento de la orden de trabajo.", exception);
    }
  }

  private WorkOrderDtos.WorkOrderDocumentResponse readDocument(WorkOrderDocument document) {
    try {
      WorkOrderDtos.WorkOrderDocumentResponse snapshot =
          objectMapper.readValue(
              document.getSnapshotContent(), WorkOrderDtos.WorkOrderDocumentResponse.class);
      return withDocumentMetadata(snapshot, document);
    } catch (JsonProcessingException exception) {
      throw new IllegalStateException(
          "No fue posible leer el documento de la orden de trabajo.", exception);
    }
  }

  private WorkOrderDtos.WorkOrderDocumentResponse withDocumentMetadata(
      WorkOrderDtos.WorkOrderDocumentResponse snapshot, WorkOrderDocument document) {
    String serviceOrderCode = snapshot.serviceOrderCode();
    if (serviceOrderCode == null || serviceOrderCode.isBlank()) {
      WorkOrder workOrder = getRequired(document.getWorkOrderId());
      serviceOrderCode =
          serviceOrderRepository
              .findById(workOrder.getServiceOrderId())
              .map(ServiceOrder::getCode)
              .orElseThrow(
                  () ->
                      new ResourceNotFoundException(
                          "la orden de servicio de la orden de trabajo",
                          workOrder.getServiceOrderId()));
    }
    return new WorkOrderDtos.WorkOrderDocumentResponse(
        document.getId(),
        document.getVersion(),
        document.getGeneratedAt(),
        snapshot.organization() == null
            ? organizationService.documentIdentity()
            : snapshot.organization(),
        snapshot.code(),
        serviceOrderCode,
        snapshot.laboratoryAreaName(),
        snapshot.responsibleName(),
        snapshot.laboratoryDueAt(),
        snapshot.samples());
  }
}
