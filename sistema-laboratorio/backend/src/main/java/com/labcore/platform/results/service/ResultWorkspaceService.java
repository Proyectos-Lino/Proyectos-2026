package com.labcore.platform.results;

import com.labcore.platform.catalog.CatalogService;
import com.labcore.platform.catalog.ResultMode;
import com.labcore.platform.catalog.TechnicalCatalogDtos;
import com.labcore.platform.catalog.TechnicalCatalogManagementService;
import com.labcore.platform.common.exception.BusinessRuleViolationException;
import com.labcore.platform.common.exception.ResourceNotFoundException;
import com.labcore.platform.orders.ServiceOrderSampleTest;
import com.labcore.platform.orders.ServiceOrderSampleTestRepository;
import com.labcore.platform.security.ApplicationUser;
import com.labcore.platform.security.ApplicationUserRepository;
import com.labcore.platform.security.RoleCode;
import com.labcore.platform.workorders.WorkOrder;
import com.labcore.platform.workorders.WorkOrderRepository;
import com.labcore.platform.workorders.WorkOrderSample;
import com.labcore.platform.workorders.WorkOrderSampleRepository;
import com.labcore.platform.workorders.WorkOrderStatus;
import com.labcore.platform.workorders.WorkOrderTest;
import com.labcore.platform.workorders.WorkOrderTestRepository;
import com.labcore.platform.workorders.WorkOrderTestStatus;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class ResultWorkspaceService {
  private static final List<WorkOrderTestStatus> ENTRY_QUEUE_STATUSES =
      List.of(
          WorkOrderTestStatus.PENDING,
          WorkOrderTestStatus.RECORDED,
          WorkOrderTestStatus.CORRECTION_REQUESTED,
          WorkOrderTestStatus.CORRECTED);
  private static final List<ResultEntryStatus> VALIDATION_QUEUE_STATUSES =
      List.of(ResultEntryStatus.RECORDED, ResultEntryStatus.CORRECTED);
  private static final List<WorkOrderStatus> NON_OPERATIONAL_WORK_ORDER_STATUSES =
      List.of(
          WorkOrderStatus.CANCELLED, WorkOrderStatus.CLOSED, WorkOrderStatus.COMMERCIAL_CORRECTION);

  private final WorkOrderTestRepository workOrderTestRepository;
  private final WorkOrderSampleRepository workOrderSampleRepository;
  private final WorkOrderRepository workOrderRepository;
  private final ServiceOrderSampleTestRepository sourceTestRepository;
  private final ResultEntryRepository resultEntryRepository;
  private final ApplicationUserRepository userRepository;
  private final CatalogService catalogService;
  private final TechnicalCatalogManagementService technicalCatalogService;

  public ResultWorkspaceService(
      WorkOrderTestRepository workOrderTestRepository,
      WorkOrderSampleRepository workOrderSampleRepository,
      WorkOrderRepository workOrderRepository,
      ServiceOrderSampleTestRepository sourceTestRepository,
      ResultEntryRepository resultEntryRepository,
      ApplicationUserRepository userRepository,
      CatalogService catalogService,
      TechnicalCatalogManagementService technicalCatalogService) {
    this.workOrderTestRepository = workOrderTestRepository;
    this.workOrderSampleRepository = workOrderSampleRepository;
    this.workOrderRepository = workOrderRepository;
    this.sourceTestRepository = sourceTestRepository;
    this.resultEntryRepository = resultEntryRepository;
    this.userRepository = userRepository;
    this.catalogService = catalogService;
    this.technicalCatalogService = technicalCatalogService;
  }

  public Page<ResultWorkspaceDtos.TechnicalResultTask> getEntryQueue(
      String actorEmail, Pageable pageable) {
    ApplicationUser actor = currentUser(actorEmail);
    Page<WorkOrderTest> queue =
        hasRole(actor, RoleCode.ADMIN)
            ? workOrderTestRepository.findOperationalResultQueue(
                ENTRY_QUEUE_STATUSES, NON_OPERATIONAL_WORK_ORDER_STATUSES, pageable)
            : workOrderTestRepository.findOperationalResultQueueForAnalyst(
                actor.getId(), ENTRY_QUEUE_STATUSES, NON_OPERATIONAL_WORK_ORDER_STATUSES, pageable);
    return toTaskPage(queue);
  }

  public Page<ResultWorkspaceDtos.TechnicalResultTask> getValidationQueue(
      String actorEmail, Pageable pageable) {
    ApplicationUser actor = currentUser(actorEmail);
    if (!hasRole(actor, RoleCode.ADMIN) && !hasRole(actor, RoleCode.VALIDATOR)) {
      throw new BusinessRuleViolationException(
          "Solo un validador o administrador puede consultar la bandeja de revisión.");
    }
    Page<ResultEntry> recordedEntries =
        resultEntryRepository.findOperationalValidationQueue(
            VALIDATION_QUEUE_STATUSES,
            actor.getId(),
            NON_OPERATIONAL_WORK_ORDER_STATUSES,
            pageable);
    if (recordedEntries.isEmpty()) {
      return new PageImpl<>(List.of(), pageable, 0);
    }
    Map<UUID, WorkOrderTest> testsById =
        workOrderTestRepository
            .findAllById(recordedEntries.stream().map(ResultEntry::getWorkOrderTestId).toList())
            .stream()
            .collect(Collectors.toMap(WorkOrderTest::getId, Function.identity()));
    List<WorkOrderTest> tests =
        recordedEntries.stream()
            .map(
                entry ->
                    required(
                        testsById, entry.getWorkOrderTestId(), "el ensayo de la orden de trabajo"))
            .toList();
    Map<UUID, ResultEntry> entriesByTest =
        recordedEntries.stream()
            .collect(Collectors.toMap(ResultEntry::getWorkOrderTestId, Function.identity()));
    List<ResultWorkspaceDtos.TechnicalResultTask> tasks = toTasks(tests, entriesByTest);
    return new PageImpl<>(tasks, pageable, recordedEntries.getTotalElements());
  }

  private Page<ResultWorkspaceDtos.TechnicalResultTask> toTaskPage(Page<WorkOrderTest> tests) {
    if (tests.isEmpty()) {
      return new PageImpl<>(List.of(), tests.getPageable(), tests.getTotalElements());
    }
    Map<UUID, ResultEntry> entriesByTest =
        resultEntryRepository
            .findByWorkOrderTestIdIn(tests.stream().map(WorkOrderTest::getId).toList())
            .stream()
            .collect(Collectors.toMap(ResultEntry::getWorkOrderTestId, Function.identity()));
    return new PageImpl<>(
        toTasks(tests.getContent(), entriesByTest), tests.getPageable(), tests.getTotalElements());
  }

  private List<ResultWorkspaceDtos.TechnicalResultTask> toTasks(
      Collection<WorkOrderTest> tests, Map<UUID, ResultEntry> entriesByTest) {
    Map<UUID, WorkOrderSample> samplesById =
        workOrderSampleRepository
            .findAllById(tests.stream().map(WorkOrderTest::getWorkOrderSampleId).toList())
            .stream()
            .collect(Collectors.toMap(WorkOrderSample::getId, Function.identity()));
    Map<UUID, WorkOrder> workOrdersById =
        workOrderRepository
            .findAllById(
                samplesById.values().stream()
                    .map(WorkOrderSample::getWorkOrderId)
                    .distinct()
                    .toList())
            .stream()
            .collect(Collectors.toMap(WorkOrder::getId, Function.identity()));
    Map<UUID, ServiceOrderSampleTest> sourceTestsById =
        sourceTestRepository
            .findAllById(tests.stream().map(WorkOrderTest::getSourceSampleTestId).toList())
            .stream()
            .collect(Collectors.toMap(ServiceOrderSampleTest::getId, Function.identity()));
    Map<UUID, ApplicationUser> usersById =
        userRepository
            .findAllById(
                tests.stream().map(WorkOrderTest::getAssignedAnalystId).collect(Collectors.toSet()))
            .stream()
            .collect(Collectors.toMap(ApplicationUser::getId, Function.identity()));

    List<UUID> recorderIds =
        entriesByTest.values().stream().map(ResultEntry::getRecordedBy).distinct().toList();
    userRepository
        .findAllById(recorderIds)
        .forEach(user -> usersById.putIfAbsent(user.getId(), user));
    List<UUID> reviewerIds =
        entriesByTest.values().stream()
            .map(ResultEntry::getCorrectionRequestedBy)
            .filter(Objects::nonNull)
            .distinct()
            .toList();
    userRepository
        .findAllById(reviewerIds)
        .forEach(user -> usersById.putIfAbsent(user.getId(), user));

    return tests.stream()
        .map(
            test ->
                toTask(
                    test,
                    entriesByTest.get(test.getId()),
                    required(samplesById, test.getWorkOrderSampleId(), "la muestra de trabajo"),
                    workOrdersById,
                    sourceTestsById,
                    usersById))
        .toList();
  }

  private ResultWorkspaceDtos.TechnicalResultTask toTask(
      WorkOrderTest test,
      ResultEntry entry,
      WorkOrderSample sample,
      Map<UUID, WorkOrder> workOrdersById,
      Map<UUID, ServiceOrderSampleTest> sourceTestsById,
      Map<UUID, ApplicationUser> usersById) {
    WorkOrder workOrder = required(workOrdersById, sample.getWorkOrderId(), "la orden de trabajo");
    ServiceOrderSampleTest sourceTest =
        required(sourceTestsById, test.getSourceSampleTestId(), "el ensayo de origen");
    ApplicationUser analyst =
        required(usersById, test.getAssignedAnalystId(), "el analista asignado");

    return new ResultWorkspaceDtos.TechnicalResultTask(
        test.getId(),
        workOrder.getId(),
        workOrder.getCode(),
        catalogService.getArea(workOrder.getLaboratoryAreaId()).getName(),
        workOrder.getLaboratoryDueAt(),
        sample.getLaboratoryCode(),
        catalogService.getSampleType(sample.getSampleTypeId()).getName(),
        catalogService.getTest(sourceTest.getTestDefinitionId()).getName(),
        catalogService.getMethod(test.getTestMethodId()).getName(),
        sourceTest.getTestDefinitionId(),
        resultMode(entry, sample, sourceTest),
        test.getStatus(),
        analyst.getFullName(),
        entry == null ? null : entry.getId(),
        entry == null ? null : entry.getStatus(),
        entry == null ? null : displayName(usersById, entry.getRecordedBy()),
        entry == null ? null : entry.getRecordedAt(),
        entry == null ? null : entry.getCorrectionRequestReason(),
        entry == null || entry.getCorrectionRequestedBy() == null
            ? null
            : displayName(usersById, entry.getCorrectionRequestedBy()),
        entry == null ? null : entry.getCorrectionRequestedByRole(),
        entry == null ? null : entry.getCorrectionRequestedAt());
  }

  private ResultMode resultMode(
      ResultEntry entry, WorkOrderSample sample, ServiceOrderSampleTest sourceTest) {
    if (entry != null) {
      return entry.getResultMode();
    }
    TechnicalCatalogDtos.TemplateResponse template =
        technicalCatalogService.findInitialTemplate(
            sourceTest.getTestDefinitionId(), sample.getSampleTypeId());
    return template == null
        ? catalogService.getSampleType(sample.getSampleTypeId()).getResultMode()
        : template.resultMode();
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

  private boolean hasRole(ApplicationUser user, RoleCode role) {
    return user.getRoles().stream().anyMatch(item -> item.getCode() == role);
  }

  private String displayName(Map<UUID, ApplicationUser> usersById, UUID userId) {
    return required(usersById, userId, "el usuario registrado").getFullName();
  }

  private <T> T required(Map<UUID, T> valuesById, UUID id, String resource) {
    T value = valuesById.get(id);
    if (value == null) {
      throw new ResourceNotFoundException(resource, id);
    }
    return value;
  }
}
