package com.labcore.platform.results;

import com.labcore.platform.catalog.CalculationScope;
import com.labcore.platform.catalog.CatalogService;
import com.labcore.platform.catalog.ResultDataType;
import com.labcore.platform.catalog.ResultFieldKind;
import com.labcore.platform.catalog.ResultMode;
import com.labcore.platform.catalog.TechnicalCatalogDtos;
import com.labcore.platform.catalog.TechnicalCatalogManagementService;
import com.labcore.platform.catalog.TestDefinition;
import com.labcore.platform.common.exception.BusinessRuleViolationException;
import com.labcore.platform.common.exception.ResourceNotFoundException;
import com.labcore.platform.orders.ServiceOrderSampleTest;
import com.labcore.platform.orders.ServiceOrderSampleTestRepository;
import com.labcore.platform.security.ApplicationUser;
import com.labcore.platform.security.ApplicationUserRepository;
import com.labcore.platform.security.RoleCode;
import com.labcore.platform.workorders.WorkOrder;
import com.labcore.platform.workorders.WorkOrderHistoryAction;
import com.labcore.platform.workorders.WorkOrderSample;
import com.labcore.platform.workorders.WorkOrderSampleRepository;
import com.labcore.platform.workorders.WorkOrderService;
import com.labcore.platform.workorders.WorkOrderStatus;
import com.labcore.platform.workorders.WorkOrderTest;
import com.labcore.platform.workorders.WorkOrderTestStatus;
import java.util.HashMap;
import java.util.HashSet;
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
public class ResultService {
  private final ResultEntryRepository entryRepository;
  private final ResultValueRepository valueRepository;
  private final ResultValidationRepository validationRepository;
  private final ResultChangeLogRepository changeLogRepository;
  private final ResultReviewHistoryRepository reviewHistoryRepository;
  private final WorkOrderService workOrderService;
  private final WorkOrderSampleRepository workOrderSampleRepository;
  private final ServiceOrderSampleTestRepository sourceTestRepository;
  private final ApplicationUserRepository userRepository;
  private final CatalogService catalogService;
  private final TechnicalCatalogManagementService technicalCatalogService;
  private final MatrixResultService matrixResultService;
  private final ResultCalculationService calculationService;

  public ResultService(
      ResultEntryRepository entryRepository,
      ResultValueRepository valueRepository,
      ResultValidationRepository validationRepository,
      ResultChangeLogRepository changeLogRepository,
      ResultReviewHistoryRepository reviewHistoryRepository,
      WorkOrderService workOrderService,
      WorkOrderSampleRepository workOrderSampleRepository,
      ServiceOrderSampleTestRepository sourceTestRepository,
      ApplicationUserRepository userRepository,
      CatalogService catalogService,
      TechnicalCatalogManagementService technicalCatalogService,
      MatrixResultService matrixResultService,
      ResultCalculationService calculationService) {
    this.entryRepository = entryRepository;
    this.valueRepository = valueRepository;
    this.validationRepository = validationRepository;
    this.changeLogRepository = changeLogRepository;
    this.reviewHistoryRepository = reviewHistoryRepository;
    this.workOrderService = workOrderService;
    this.workOrderSampleRepository = workOrderSampleRepository;
    this.sourceTestRepository = sourceTestRepository;
    this.userRepository = userRepository;
    this.catalogService = catalogService;
    this.technicalCatalogService = technicalCatalogService;
    this.matrixResultService = matrixResultService;
    this.calculationService = calculationService;
  }

  public ResultDtos.ResultResponse startMatrixExecution(
      UUID workOrderTestId, ResultDtos.StartMatrixExecutionRequest request, String actorEmail) {
    ApplicationUser actor = currentUser(actorEmail);
    WorkOrderTest workOrderTest = workOrderService.getRequiredTest(workOrderTestId);
    ensureCanRecord(actor, workOrderTest);
    if (entryRepository.findByWorkOrderTestId(workOrderTestId).isPresent()) {
      throw new BusinessRuleViolationException("La evaluación del ensayo ya fue iniciada.");
    }
    TechnicalCatalogDtos.TemplateResponse template = requiredMatrixTemplate(workOrderTest);
    ResultEntry entry =
        new ResultEntry(workOrderTestId, template.id(), ResultMode.MATRIX, null, actor.getId());
    entry.markDraft();
    entry = entryRepository.save(entry);
    matrixResultService.start(
        entry,
        request.declaredRowCount(),
        request.declaredColumnCount(),
        template,
        actor.getId(),
        actionRole(actor, RoleCode.ANALYST));
    return toResponse(entry);
  }

  public ResultDtos.ResultResponse saveMatrixDraft(
      UUID workOrderTestId, ResultDtos.SaveMatrixDraftRequest request, String actorEmail) {
    ApplicationUser actor = currentUser(actorEmail);
    WorkOrderTest workOrderTest = workOrderService.getRequiredTest(workOrderTestId);
    ensureCanRecord(actor, workOrderTest);
    ResultEntry entry = requiredEntry(workOrderTestId);
    if (entry.getResultMode() != ResultMode.MATRIX
        || entry.getStatus() != ResultEntryStatus.DRAFT) {
      throw new BusinessRuleViolationException(
          "Solo una evaluación matricial en curso admite guardados parciales.");
    }
    TechnicalCatalogDtos.TemplateResponse template =
        technicalCatalogService.getTemplate(entry.getResultTemplateId());
    matrixResultService.saveDraft(
        entry,
        request.matrix(),
        template,
        request.adjustmentReason(),
        actor.getId(),
        actionRole(actor, RoleCode.ANALYST));
    entry.revise(trimToNull(request.analystObservation()));
    return toResponse(entry);
  }

  public ResultDtos.ResultResponse record(
      UUID workOrderTestId, ResultDtos.RecordResultRequest request, String actorEmail) {
    ApplicationUser actor = currentUser(actorEmail);
    WorkOrderTest workOrderTest = workOrderService.getRequiredTest(workOrderTestId);
    ensureCanRecord(actor, workOrderTest);
    ServiceOrderSampleTest sourceTest =
        sourceTestRepository
            .findById(workOrderTest.getSourceSampleTestId())
            .orElseThrow(
                () ->
                    new ResourceNotFoundException(
                        "el ensayo de origen", workOrderTest.getSourceSampleTestId()));
    TestDefinition definition = catalogService.getTest(sourceTest.getTestDefinitionId());
    ResultEntry entry = entryRepository.findByWorkOrderTestId(workOrderTestId).orElse(null);
    TechnicalCatalogDtos.TemplateResponse template =
        entry != null && entry.getResultTemplateId() != null
            ? technicalCatalogService.getTemplate(entry.getResultTemplateId())
            : findTemplate(workOrderTest, sourceTest.getTestDefinitionId());
    boolean isDraftCompletion = entry != null && entry.getStatus() == ResultEntryStatus.DRAFT;
    boolean isCorrection = entry != null && !isDraftCompletion;
    if (entry != null && entry.getStatus() == ResultEntryStatus.VALIDATED) {
      throw new BusinessRuleViolationException(
          "No se puede modificar un resultado que ya fue validado.");
    }
    ResultEntryStatus previousStatus = entry == null ? null : entry.getStatus();
    String actorRole = actionRole(actor, RoleCode.ANALYST);
    boolean changed;
    if (entry == null) {
      ResultMode resultMode = template == null ? ResultMode.SCALAR : template.resultMode();
      entry =
          entryRepository.save(
              new ResultEntry(
                  workOrderTestId,
                  template == null ? null : template.id(),
                  resultMode,
                  trimToNull(request.analystObservation()),
                  actor.getId()));
    }
    String observation = trimToNull(request.analystObservation());
    int targetRevision = isCorrection ? entry.getResultRevision() + 1 : entry.getResultRevision();
    boolean observationChanged =
        isCorrection && !Objects.equals(entry.getAnalystObservation(), observation);
    if (isCorrection && trimToNull(request.revisionReason()) == null) {
      throw new BusinessRuleViolationException(
          previousStatus == ResultEntryStatus.CORRECTION_REQUESTED
              ? "Debe explicar la corrección realizada antes de reenviar el resultado."
              : "Debe registrar el motivo al corregir un resultado previamente ingresado.");
    }
    if (entry.getResultMode() == ResultMode.MATRIX) {
      if (template == null || request.matrix() == null) {
        throw new BusinessRuleViolationException(
            "La plantilla matricial y todos los datos de la matriz son obligatorios.");
      }
      changed =
          matrixResultService.save(
              entry,
              request.matrix(),
              template,
              request.revisionReason(),
              actor.getId(),
              actorRole);
    } else {
      ResultCalculationService.PreparedResult prepared =
          calculationService.prepare(template, request.values());
      validateValues(prepared.values(), definition, entry.getResultMode(), template);
      changed =
          replaceValues(
              entry,
              prepared.values(),
              actor.getId(),
              actorRole,
              request.revisionReason(),
              isCorrection);
      calculationService.persistSnapshots(
          entry, targetRevision, prepared.calculations(), actor.getId(), actorRole);
    }
    if (isCorrection && !changed && !observationChanged) {
      throw new BusinessRuleViolationException(
          "No se detectaron cambios para registrar como corrección.");
    }
    ResultReviewAction reviewAction;
    if (isCorrection) {
      entry.markCorrected(observation);
      workOrderTest.markCorrected();
      reviewAction = ResultReviewAction.CORRECTED;
    } else if (isDraftCompletion) {
      entry.markRecorded(observation);
      workOrderTest.markRecorded();
      reviewAction = ResultReviewAction.REGISTERED;
    } else {
      workOrderTest.markRecorded();
      reviewAction = ResultReviewAction.REGISTERED;
    }
    sourceTest.markCompleted();
    saveReviewHistory(
        entry, reviewAction, previousStatus, request.revisionReason(), null, actor, actorRole);
    WorkOrderSample workOrderSample = requiredWorkOrderSample(workOrderTest);
    boolean isResubmission = previousStatus == ResultEntryStatus.CORRECTION_REQUESTED;
    workOrderService.synchronizeStatus(
        workOrderSample.getWorkOrderId(),
        actorEmail,
        !isCorrection
            ? WorkOrderHistoryAction.RESULT_RECORDED
            : isResubmission
                ? WorkOrderHistoryAction.RESULT_RESUBMITTED
                : WorkOrderHistoryAction.RESULT_CORRECTED,
        "Resultado «%s» de la muestra %s %s%s."
            .formatted(
                definition.getName(),
                workOrderSample.getLaboratoryCode(),
                !isCorrection
                    ? "registrado"
                    : isResubmission ? "corregido y reenviado" : "corregido",
                isCorrection
                    ? " (versión %d; motivo: %s)"
                        .formatted(entry.getResultRevision(), trimToNull(request.revisionReason()))
                    : ""));
    return toResponse(entry);
  }

  public ResultDtos.ResultResponse validate(
      UUID workOrderTestId, ResultDtos.ValidateResultRequest request, String actorEmail) {
    ApplicationUser actor = currentUser(actorEmail);
    WorkOrderTest workOrderTest = workOrderService.getRequiredTest(workOrderTestId);
    ensureCanValidate(actor, workOrderTest);
    ResultEntry entry =
        entryRepository
            .findByWorkOrderTestId(workOrderTestId)
            .orElseThrow(
                () ->
                    new BusinessRuleViolationException(
                        "Primero debe registrarse el resultado del ensayo."));
    if (entry.getStatus() == ResultEntryStatus.VALIDATED) {
      throw new BusinessRuleViolationException("El resultado ya fue validado.");
    }
    ensureReviewable(entry);
    ensureIndependentReviewer(entry, actor);
    String actorRole = actionRole(actor, RoleCode.VALIDATOR);
    validationRepository.save(
        new ResultValidation(
            entry.getId(),
            actor.getId(),
            actorRole,
            entry.getResultRevision(),
            trimToNull(request.comment())));
    ResultEntryStatus previousStatus = entry.getStatus();
    entry.validate();
    workOrderTest.markValidated();
    saveReviewHistory(
        entry,
        ResultReviewAction.VALIDATED,
        previousStatus,
        null,
        request.comment(),
        actor,
        actorRole);
    WorkOrderSample workOrderSample = requiredWorkOrderSample(workOrderTest);
    ServiceOrderSampleTest sourceTest =
        sourceTestRepository
            .findById(workOrderTest.getSourceSampleTestId())
            .orElseThrow(
                () ->
                    new ResourceNotFoundException(
                        "el ensayo de origen", workOrderTest.getSourceSampleTestId()));
    String testName = catalogService.getTest(sourceTest.getTestDefinitionId()).getName();
    workOrderService.synchronizeStatus(
        workOrderSample.getWorkOrderId(),
        actorEmail,
        WorkOrderHistoryAction.RESULT_VALIDATED,
        "Resultado «%s» de la muestra %s validado."
            .formatted(testName, workOrderSample.getLaboratoryCode()));
    return toResponse(entry);
  }

  public ResultDtos.ResultResponse reject(
      UUID workOrderTestId, ResultDtos.RejectResultRequest request, String actorEmail) {
    ApplicationUser actor = currentUser(actorEmail);
    WorkOrderTest workOrderTest = workOrderService.getRequiredTest(workOrderTestId);
    ensureCanValidate(actor, workOrderTest);
    ResultEntry entry = requiredEntry(workOrderTestId);
    ensureReviewable(entry);
    ensureIndependentReviewer(entry, actor);
    String reason = requiredReason(request.reason());
    String actorRole = actionRole(actor, RoleCode.VALIDATOR);
    ResultEntryStatus previousStatus = entry.getStatus();
    entry.requestCorrection(reason, actor.getId(), actorRole);
    workOrderTest.requestCorrection();
    saveReviewHistory(
        entry,
        ResultReviewAction.CORRECTION_REQUESTED,
        previousStatus,
        reason,
        null,
        actor,
        actorRole);
    ResultContext context = resultContext(workOrderTest);
    workOrderService.synchronizeStatus(
        context.sample().getWorkOrderId(),
        actorEmail,
        WorkOrderHistoryAction.RESULT_CORRECTION_REQUESTED,
        "Resultado «%s» de la muestra %s devuelto al analista. Motivo: %s."
            .formatted(
                context.definition().getName(), context.sample().getLaboratoryCode(), reason));
    return toResponse(entry);
  }

  public ResultDtos.ResultResponse correctAndValidate(
      UUID workOrderTestId, ResultDtos.CorrectAndValidateResultRequest request, String actorEmail) {
    ApplicationUser actor = currentUser(actorEmail);
    WorkOrderTest workOrderTest = workOrderService.getRequiredTest(workOrderTestId);
    ensureCanValidate(actor, workOrderTest);
    ResultEntry entry = requiredEntry(workOrderTestId);
    ensureReviewable(entry);
    ensureIndependentReviewer(entry, actor);
    String reason = requiredReason(request.reason());
    String actorRole = actionRole(actor, RoleCode.VALIDATOR);
    ResultContext context = resultContext(workOrderTest);
    TechnicalCatalogDtos.TemplateResponse template =
        entry.getResultTemplateId() == null
            ? findTemplate(workOrderTest, context.sourceTest().getTestDefinitionId())
            : technicalCatalogService.getTemplate(entry.getResultTemplateId());
    boolean changed;
    if (entry.getResultMode() == ResultMode.MATRIX) {
      if (template == null || request.matrix() == null) {
        throw new BusinessRuleViolationException(
            "La plantilla matricial y todos los datos de la matriz son obligatorios.");
      }
      changed =
          matrixResultService.save(
              entry, request.matrix(), template, reason, actor.getId(), actorRole);
    } else {
      ResultCalculationService.PreparedResult prepared =
          calculationService.prepare(template, request.values());
      validateValues(prepared.values(), context.definition(), entry.getResultMode(), template);
      changed = replaceValues(entry, prepared.values(), actor.getId(), actorRole, reason, true);
      calculationService.persistSnapshots(
          entry, entry.getResultRevision() + 1, prepared.calculations(), actor.getId(), actorRole);
    }
    String observation = trimToNull(request.analystObservation());
    boolean observationChanged = !Objects.equals(entry.getAnalystObservation(), observation);
    if (!changed && !observationChanged) {
      throw new BusinessRuleViolationException(
          "Modifica al menos un dato antes de corregir y validar.");
    }
    ResultEntryStatus previousStatus = entry.getStatus();
    entry.registerValidatorCorrection(observation);
    validationRepository.save(
        new ResultValidation(
            entry.getId(),
            actor.getId(),
            actorRole,
            entry.getResultRevision(),
            trimToNull(request.comment())));
    entry.validate();
    workOrderTest.markValidated();
    saveReviewHistory(
        entry,
        ResultReviewAction.CORRECTED_AND_VALIDATED,
        previousStatus,
        reason,
        request.comment(),
        actor,
        actorRole);
    workOrderService.synchronizeStatus(
        context.sample().getWorkOrderId(),
        actorEmail,
        WorkOrderHistoryAction.RESULT_CORRECTED_AND_VALIDATED,
        "Resultado «%s» de la muestra %s corregido por el validador y validado como versión %d. Motivo: %s."
            .formatted(
                context.definition().getName(),
                context.sample().getLaboratoryCode(),
                entry.getResultRevision(),
                reason));
    return toResponse(entry);
  }

  @Transactional(readOnly = true)
  public ResultDtos.ResultResponse getByWorkOrderTest(UUID workOrderTestId, String actorEmail) {
    ApplicationUser actor = currentUser(actorEmail);
    WorkOrderTest workOrderTest = workOrderService.getRequiredTest(workOrderTestId);
    ensureCanView(actor, workOrderTest);
    ResultEntry entry =
        entryRepository
            .findByWorkOrderTestId(workOrderTestId)
            .orElseThrow(() -> new ResourceNotFoundException("el resultado", workOrderTestId));
    return toResponse(entry);
  }

  @Transactional(readOnly = true)
  public TechnicalCatalogDtos.TemplateResponse getInitialTemplate(
      UUID workOrderTestId, String actorEmail) {
    ApplicationUser actor = currentUser(actorEmail);
    WorkOrderTest workOrderTest = workOrderService.getRequiredTest(workOrderTestId);
    ensureCanView(actor, workOrderTest);
    ResultEntry existingEntry = entryRepository.findByWorkOrderTestId(workOrderTestId).orElse(null);
    if (existingEntry != null && existingEntry.getResultTemplateId() != null) {
      return technicalCatalogService.getTemplate(existingEntry.getResultTemplateId());
    }
    ServiceOrderSampleTest sourceTest =
        sourceTestRepository
            .findById(workOrderTest.getSourceSampleTestId())
            .orElseThrow(
                () ->
                    new ResourceNotFoundException(
                        "el ensayo de origen", workOrderTest.getSourceSampleTestId()));
    UUID sampleTypeId =
        workOrderSampleRepository
            .findById(workOrderTest.getWorkOrderSampleId())
            .map(WorkOrderSample::getSampleTypeId)
            .orElseThrow(
                () ->
                    new ResourceNotFoundException(
                        "la muestra de trabajo", workOrderTest.getWorkOrderSampleId()));
    return technicalCatalogService.findInitialTemplate(
        sourceTest.getTestDefinitionId(), sampleTypeId);
  }

  @Transactional(readOnly = true)
  public ResultDtos.CalculationPreviewResponse previewCalculations(
      UUID workOrderTestId, ResultDtos.CalculationPreviewRequest request, String actorEmail) {
    TechnicalCatalogDtos.TemplateResponse template =
        getInitialTemplate(workOrderTestId, actorEmail);
    if (template == null) {
      return new ResultDtos.CalculationPreviewResponse(List.of());
    }
    return calculationService.preview(template, request.values());
  }

  private ResultDtos.ResultResponse toResponse(ResultEntry entry) {
    TechnicalCatalogDtos.TemplateResponse template =
        entry.getResultTemplateId() == null
            ? null
            : technicalCatalogService.getTemplate(entry.getResultTemplateId());
    List<ResultValue> storedValues =
        valueRepository.findByResultEntryIdOrderByPositionAsc(entry.getId());
    List<ResultDtos.ValueResponse> values =
        storedValues.stream()
            .map(
                value ->
                    new ResultDtos.ValueResponse(
                        value.getId(),
                        value.getFieldKey(),
                        value.getLabel(),
                        value.getPosition(),
                        value.getRepetitionIndex(),
                        value.getValueText(),
                        value.getValueNumber(),
                        value.getMeasurementUnitId(),
                        value.getFieldKind(),
                        value.getFormulaUsed(),
                        value.getCalculationInputs()))
            .toList();
    Map<UUID, ResultValue> valuesById =
        storedValues.stream().collect(Collectors.toMap(ResultValue::getId, Function.identity()));
    ResultValidation storedValidation =
        validationRepository.findByResultEntryId(entry.getId()).orElse(null);
    List<ResultReviewHistory> reviewHistory =
        reviewHistoryRepository.findByResultEntryIdOrderByPerformedAtDesc(entry.getId());
    Set<UUID> userIds = new HashSet<>();
    userIds.add(entry.getRecordedBy());
    if (entry.getCorrectionRequestedBy() != null) {
      userIds.add(entry.getCorrectionRequestedBy());
    }
    if (storedValidation != null) {
      userIds.add(storedValidation.getValidatorId());
    }
    List<ResultChangeLog> corrections =
        valuesById.isEmpty()
            ? List.of()
            : changeLogRepository.findByResultValueIdInOrderByChangedAtDesc(valuesById.keySet());
    corrections.forEach(correction -> userIds.add(correction.getChangedBy()));
    reviewHistory.forEach(review -> userIds.add(review.getPerformedBy()));
    Map<UUID, ApplicationUser> usersById =
        userRepository.findAllById(userIds).stream()
            .collect(Collectors.toMap(ApplicationUser::getId, Function.identity()));

    List<ResultDtos.CorrectionResponse> correctionResponses =
        corrections.stream()
            .map(
                correction -> {
                  ResultValue value = valuesById.get(correction.getResultValueId());
                  return new ResultDtos.CorrectionResponse(
                      correction.getId(),
                      correction.getResultValueId(),
                      value == null ? "Campo de resultado" : value.getLabel(),
                      correction.getOldValueText(),
                      correction.getOldValueNumber(),
                      correction.getNewValueText(),
                      correction.getNewValueNumber(),
                      correction.getReason(),
                      correction.getChangedBy(),
                      userName(usersById, correction.getChangedBy()),
                      correction.getChangedByRole(),
                      correction.getChangedAt());
                })
            .toList();
    ResultDtos.ValidationResponse validation =
        storedValidation == null
            ? null
            : new ResultDtos.ValidationResponse(
                storedValidation.getValidatorId(),
                userName(usersById, storedValidation.getValidatorId()),
                storedValidation.getValidatorRole(),
                storedValidation.getResultRevision(),
                storedValidation.getValidatedAt(),
                storedValidation.getComment());
    List<ResultDtos.ReviewHistoryResponse> reviewHistoryResponses =
        reviewHistory.stream()
            .map(
                review ->
                    new ResultDtos.ReviewHistoryResponse(
                        review.getId(),
                        review.getAction(),
                        review.getPreviousStatus(),
                        review.getNewStatus(),
                        review.getResultRevision(),
                        review.getReason(),
                        review.getComment(),
                        review.getPerformedBy(),
                        userName(usersById, review.getPerformedBy()),
                        review.getPerformedByRole(),
                        review.getPerformedAt()))
            .toList();
    return new ResultDtos.ResultResponse(
        entry.getId(),
        entry.getWorkOrderTestId(),
        entry.getResultTemplateId(),
        entry.getResultMode(),
        entry.getStatus(),
        entry.getResultRevision(),
        entry.getAnalystObservation(),
        entry.getRecordedBy(),
        userName(usersById, entry.getRecordedBy()),
        entry.getRecordedAt(),
        values,
        correctionResponses,
        matrixResultService.getMatrix(entry.getId(), template),
        matrixResultService.getCorrections(entry.getId()),
        calculationService.snapshots(entry.getId()),
        entry.getCorrectionRequestReason(),
        entry.getCorrectionRequestedBy(),
        userName(usersById, entry.getCorrectionRequestedBy()),
        entry.getCorrectionRequestedByRole(),
        entry.getCorrectionRequestedAt(),
        reviewHistoryResponses,
        validation);
  }

  private boolean replaceValues(
      ResultEntry entry,
      List<ResultDtos.ValueRequest> values,
      UUID actorId,
      String actorRole,
      String revisionReason,
      boolean isCorrection) {
    Map<String, ResultValue> existingValues = new HashMap<>();
    for (ResultValue value : valueRepository.findByResultEntryIdOrderByPositionAsc(entry.getId())) {
      existingValues.put(valueKey(value.getFieldKey(), value.getRepetitionIndex()), value);
    }
    Set<String> requestedKeys =
        values.stream()
            .map(value -> valueKey(value.fieldKey().trim(), value.repetitionIndex()))
            .collect(Collectors.toSet());
    if (!requestedKeys.containsAll(existingValues.keySet())) {
      throw new BusinessRuleViolationException(
          "No es posible eliminar lecturas previamente registradas.");
    }
    String reason = trimToNull(revisionReason);
    int position = 1;
    boolean anyChanged = false;
    for (ResultDtos.ValueRequest input : values) {
      String fieldKey = input.fieldKey().trim();
      ResultValue current = existingValues.get(valueKey(fieldKey, input.repetitionIndex()));
      String text = trimToNull(input.valueText());
      if (current == null) {
        if (isCorrection && reason == null) {
          throw new BusinessRuleViolationException(
              "Debe registrar el motivo al agregar o corregir una lectura.");
        }
        ResultValue created =
            valueRepository.save(
                new ResultValue(
                    entry.getId(),
                    fieldKey,
                    input.label().trim(),
                    position++,
                    input.repetitionIndex(),
                    text,
                    input.valueNumber(),
                    input.measurementUnitId()));
        if (isCorrection) {
          changeLogRepository.save(
              new ResultChangeLog(
                  created.getId(),
                  null,
                  null,
                  text,
                  input.valueNumber(),
                  reason,
                  actorId,
                  actorRole));
        }
        anyChanged = true;
        continue;
      }
      boolean changed =
          !Objects.equals(current.getValueText(), text)
              || !Objects.equals(current.getValueNumber(), input.valueNumber())
              || !Objects.equals(current.getMeasurementUnitId(), input.measurementUnitId());
      if (changed) {
        if (reason == null) {
          throw new BusinessRuleViolationException(
              "Debe registrar el motivo al corregir un resultado previamente ingresado.");
        }
        changeLogRepository.save(
            new ResultChangeLog(current, text, input.valueNumber(), reason, actorId, actorRole));
        anyChanged = true;
      }
      current.replace(
          input.label().trim(), position++, text, input.valueNumber(), input.measurementUnitId());
    }
    return anyChanged;
  }

  private void validateValues(
      List<ResultDtos.ValueRequest> values,
      TestDefinition definition,
      ResultMode resultMode,
      TechnicalCatalogDtos.TemplateResponse template) {
    if (values == null || values.isEmpty()) {
      throw new BusinessRuleViolationException("Debe registrar al menos un valor de resultado.");
    }
    Set<String> fieldKeys = new HashSet<>();
    for (ResultDtos.ValueRequest value : values) {
      if (value.repetitionIndex() < 0
          || (resultMode == ResultMode.SCALAR && value.repetitionIndex() != 0)) {
        throw new BusinessRuleViolationException(
            "El índice de lectura no es válido para el modo configurado.");
      }
      if (!fieldKeys.add(valueKey(value.fieldKey().trim(), value.repetitionIndex()))) {
        throw new BusinessRuleViolationException(
            "Cada lectura debe tener una clave y un índice únicos.");
      }
      ResultDataType resultType = resultTypeFor(value.fieldKey(), definition, template);
      if (resultType == ResultDataType.NUMERIC && value.valueNumber() == null) {
        throw new BusinessRuleViolationException(
            "El ensayo " + definition.getName() + " requiere un valor numérico.");
      }
      if (resultType != ResultDataType.NUMERIC && trimToNull(value.valueText()) == null) {
        throw new BusinessRuleViolationException(
            "El ensayo " + definition.getName() + " requiere un valor de texto.");
      }
      if (value.measurementUnitId() != null) catalogService.getUnit(value.measurementUnitId());
    }
    if (template != null) {
      validateTemplateFields(values, template);
    }
    if (resultMode == ResultMode.REPEATABLE && template != null) {
      long readings =
          values.stream().map(ResultDtos.ValueRequest::repetitionIndex).distinct().count();
      if (readings < template.minimumRepetitions()
          || (template.maximumRepetitions() != null && readings > template.maximumRepetitions())) {
        throw new BusinessRuleViolationException(
            "La cantidad de lecturas no cumple los límites configurados en la plantilla.");
      }
    }
  }

  private ResultDataType resultTypeFor(
      String fieldKey, TestDefinition definition, TechnicalCatalogDtos.TemplateResponse template) {
    if (template == null) {
      return definition.getResultType();
    }
    return template.fields().stream()
        .filter(field -> field.fieldKey().equalsIgnoreCase(fieldKey.trim()))
        .map(TechnicalCatalogDtos.TemplateFieldResponse::resultType)
        .findFirst()
        .orElseThrow(
            () ->
                new BusinessRuleViolationException(
                    "El resultado contiene un campo que no pertenece a la plantilla."));
  }

  private void validateTemplateFields(
      List<ResultDtos.ValueRequest> values, TechnicalCatalogDtos.TemplateResponse template) {
    Map<Integer, Set<String>> fieldsByReading =
        values.stream()
            .collect(
                Collectors.groupingBy(
                    ResultDtos.ValueRequest::repetitionIndex,
                    Collectors.mapping(
                        value -> value.fieldKey().trim().toLowerCase(java.util.Locale.ROOT),
                        Collectors.toSet())));
    Set<String> requiredPerReading =
        template.fields().stream()
            .filter(TechnicalCatalogDtos.TemplateFieldResponse::required)
            .filter(field -> field.fieldKind() != ResultFieldKind.INFORMATIONAL)
            .filter(
                field ->
                    field.fieldKind() != ResultFieldKind.CALCULATED
                        || field.calculationScope() != CalculationScope.AGGREGATE)
            .map(field -> field.fieldKey().trim().toLowerCase(java.util.Locale.ROOT))
            .collect(Collectors.toSet());
    if (fieldsByReading.values().stream().anyMatch(keys -> !keys.containsAll(requiredPerReading))) {
      throw new BusinessRuleViolationException(
          "Cada lectura debe incluir todos los campos obligatorios de la plantilla.");
    }
    Set<String> requiredAggregate =
        template.fields().stream()
            .filter(TechnicalCatalogDtos.TemplateFieldResponse::required)
            .filter(field -> field.fieldKind() == ResultFieldKind.CALCULATED)
            .filter(field -> field.calculationScope() == CalculationScope.AGGREGATE)
            .map(field -> field.fieldKey().trim().toLowerCase(java.util.Locale.ROOT))
            .collect(Collectors.toSet());
    if (!fieldsByReading.getOrDefault(0, Set.of()).containsAll(requiredAggregate)) {
      throw new BusinessRuleViolationException(
          "Falta uno o más resultados calculados globales obligatorios.");
    }
  }

  private TechnicalCatalogDtos.TemplateResponse findTemplate(
      WorkOrderTest workOrderTest, UUID testDefinitionId) {
    UUID sampleTypeId =
        workOrderSampleRepository
            .findById(workOrderTest.getWorkOrderSampleId())
            .map(WorkOrderSample::getSampleTypeId)
            .orElseThrow(
                () ->
                    new ResourceNotFoundException(
                        "la muestra de trabajo", workOrderTest.getWorkOrderSampleId()));
    return technicalCatalogService.findInitialTemplate(testDefinitionId, sampleTypeId);
  }

  private String valueKey(String fieldKey, int repetitionIndex) {
    return fieldKey + ":" + repetitionIndex;
  }

  private ResultEntry requiredEntry(UUID workOrderTestId) {
    return entryRepository
        .findByWorkOrderTestId(workOrderTestId)
        .orElseThrow(
            () ->
                new BusinessRuleViolationException(
                    "Primero debe registrarse el resultado del ensayo."));
  }

  private ResultContext resultContext(WorkOrderTest workOrderTest) {
    WorkOrderSample sample = requiredWorkOrderSample(workOrderTest);
    ServiceOrderSampleTest sourceTest =
        sourceTestRepository
            .findById(workOrderTest.getSourceSampleTestId())
            .orElseThrow(
                () ->
                    new ResourceNotFoundException(
                        "el ensayo de origen", workOrderTest.getSourceSampleTestId()));
    return new ResultContext(
        sample, sourceTest, catalogService.getTest(sourceTest.getTestDefinitionId()));
  }

  private void ensureReviewable(ResultEntry entry) {
    if (entry.getStatus() == ResultEntryStatus.DRAFT) {
      throw new BusinessRuleViolationException(
          "La evaluación aún está en ejecución y no puede enviarse a revisión.");
    }
    if (entry.getStatus() == ResultEntryStatus.CORRECTION_REQUESTED) {
      throw new BusinessRuleViolationException(
          "El resultado está devuelto al analista y debe corregirse antes de una nueva revisión.");
    }
    if (entry.getStatus() == ResultEntryStatus.VALIDATED) {
      throw new BusinessRuleViolationException("El resultado ya fue validado.");
    }
  }

  private void ensureIndependentReviewer(ResultEntry entry, ApplicationUser actor) {
    if (entry.getRecordedBy().equals(actor.getId())) {
      throw new BusinessRuleViolationException(
          "La persona que ingresó el resultado no puede validarlo.");
    }
  }

  private String requiredReason(String value) {
    String reason = trimToNull(value);
    if (reason == null) {
      throw new BusinessRuleViolationException("El motivo es obligatorio para esta acción.");
    }
    return reason;
  }

  private TechnicalCatalogDtos.TemplateResponse requiredMatrixTemplate(
      WorkOrderTest workOrderTest) {
    ServiceOrderSampleTest sourceTest =
        sourceTestRepository
            .findById(workOrderTest.getSourceSampleTestId())
            .orElseThrow(
                () ->
                    new ResourceNotFoundException(
                        "el ensayo de origen", workOrderTest.getSourceSampleTestId()));
    TechnicalCatalogDtos.TemplateResponse template =
        findTemplate(workOrderTest, sourceTest.getTestDefinitionId());
    if (template == null || template.resultMode() != ResultMode.MATRIX) {
      throw new BusinessRuleViolationException(
          "El ensayo no tiene una plantilla matricial activa.");
    }
    return template;
  }

  private void saveReviewHistory(
      ResultEntry entry,
      ResultReviewAction action,
      ResultEntryStatus previousStatus,
      String reason,
      String comment,
      ApplicationUser actor,
      String actorRole) {
    reviewHistoryRepository.save(
        new ResultReviewHistory(
            entry.getId(),
            action,
            previousStatus,
            entry.getStatus(),
            entry.getResultRevision(),
            trimToNull(reason),
            trimToNull(comment),
            actor.getId(),
            actorRole));
  }

  private String actionRole(ApplicationUser actor, RoleCode preferredRole) {
    if (hasRole(actor, preferredRole)) {
      return preferredRole.name();
    }
    if (hasRole(actor, RoleCode.ADMIN)) {
      return RoleCode.ADMIN.name();
    }
    return actor.getRoles().stream()
        .map(role -> role.getCode().name())
        .sorted()
        .findFirst()
        .orElse("UNKNOWN");
  }

  private void ensureCanRecord(ApplicationUser actor, WorkOrderTest test) {
    boolean isAdmin = hasRole(actor, RoleCode.ADMIN);
    if (!isAdmin && !hasRole(actor, RoleCode.ANALYST)) {
      throw new BusinessRuleViolationException(
          "Solo un analista asignado o administrador puede registrar resultados.");
    }
    if (!isAdmin && !test.getAssignedAnalystId().equals(actor.getId())) {
      throw new BusinessRuleViolationException(
          "El resultado solo puede ser registrado por el analista asignado.");
    }
    if (test.getStatus() == WorkOrderTestStatus.VALIDATED) {
      throw new BusinessRuleViolationException("No se puede modificar un ensayo validado.");
    }
    WorkOrderStatus workOrderStatus = getWorkOrder(test).getStatus();
    if (workOrderStatus == WorkOrderStatus.CANCELLED
        || workOrderStatus == WorkOrderStatus.CLOSED
        || workOrderStatus == WorkOrderStatus.COMMERCIAL_CORRECTION) {
      throw new BusinessRuleViolationException(
          "No se pueden registrar resultados mientras la orden está cerrada, anulada o en corrección comercial.");
    }
  }

  private void ensureCanView(ApplicationUser actor, WorkOrderTest test) {
    boolean canViewAll =
        hasRole(actor, RoleCode.ADMIN)
            || hasRole(actor, RoleCode.TECHNICAL_MANAGER)
            || hasRole(actor, RoleCode.VALIDATOR);
    if (!canViewAll && !test.getAssignedAnalystId().equals(actor.getId())) {
      throw new BusinessRuleViolationException(
          "Solo puedes consultar resultados de ensayos asignados a tu usuario.");
    }
  }

  private void ensureCanValidate(ApplicationUser actor, WorkOrderTest test) {
    boolean isAdmin = hasRole(actor, RoleCode.ADMIN);
    if (!isAdmin && !hasRole(actor, RoleCode.VALIDATOR)) {
      throw new BusinessRuleViolationException(
          "Solo un validador o administrador puede validar resultados.");
    }
    WorkOrderStatus workOrderStatus = getWorkOrder(test).getStatus();
    if (workOrderStatus == WorkOrderStatus.CANCELLED
        || workOrderStatus == WorkOrderStatus.CLOSED
        || workOrderStatus == WorkOrderStatus.COMMERCIAL_CORRECTION) {
      throw new BusinessRuleViolationException(
          "No se pueden validar resultados mientras la orden está cerrada, anulada o en corrección comercial.");
    }
  }

  private WorkOrder getWorkOrder(WorkOrderTest test) {
    return workOrderSampleRepository
        .findById(test.getWorkOrderSampleId())
        .map(WorkOrderSample::getWorkOrderId)
        .map(workOrderService::getRequired)
        .orElseThrow(
            () ->
                new ResourceNotFoundException(
                    "la muestra de trabajo", test.getWorkOrderSampleId()));
  }

  private WorkOrderSample requiredWorkOrderSample(WorkOrderTest test) {
    return workOrderSampleRepository
        .findById(test.getWorkOrderSampleId())
        .orElseThrow(
            () ->
                new ResourceNotFoundException(
                    "la muestra de trabajo", test.getWorkOrderSampleId()));
  }

  private String userName(Map<UUID, ApplicationUser> usersById, UUID userId) {
    if (userId == null) {
      return null;
    }
    ApplicationUser user = usersById.get(userId);
    return user == null ? "Usuario no disponible" : user.getFullName();
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

  private String trimToNull(String value) {
    return value == null || value.isBlank() ? null : value.trim();
  }

  private record ResultContext(
      WorkOrderSample sample, ServiceOrderSampleTest sourceTest, TestDefinition definition) {}
}
