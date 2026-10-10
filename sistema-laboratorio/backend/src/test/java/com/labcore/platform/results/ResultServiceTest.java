package com.labcore.platform.results;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.labcore.platform.catalog.CatalogService;
import com.labcore.platform.catalog.ResultDataType;
import com.labcore.platform.catalog.TechnicalCatalogManagementService;
import com.labcore.platform.catalog.TestDefinition;
import com.labcore.platform.common.exception.BusinessRuleViolationException;
import com.labcore.platform.orders.ServiceOrderSampleTest;
import com.labcore.platform.orders.ServiceOrderSampleTestRepository;
import com.labcore.platform.security.ApplicationRole;
import com.labcore.platform.security.ApplicationUser;
import com.labcore.platform.security.ApplicationUserRepository;
import com.labcore.platform.security.RoleCode;
import com.labcore.platform.workorders.WorkOrder;
import com.labcore.platform.workorders.WorkOrderSample;
import com.labcore.platform.workorders.WorkOrderSampleRepository;
import com.labcore.platform.workorders.WorkOrderService;
import com.labcore.platform.workorders.WorkOrderTest;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ResultServiceTest {
  @Mock private ResultEntryRepository entryRepository;
  @Mock private ResultValueRepository valueRepository;
  @Mock private ResultValidationRepository validationRepository;
  @Mock private ResultChangeLogRepository changeLogRepository;
  @Mock private ResultReviewHistoryRepository reviewHistoryRepository;
  @Mock private WorkOrderService workOrderService;
  @Mock private WorkOrderSampleRepository workOrderSampleRepository;
  @Mock private ServiceOrderSampleTestRepository sourceTestRepository;
  @Mock private ApplicationUserRepository userRepository;
  @Mock private CatalogService catalogService;
  @Mock private TechnicalCatalogManagementService technicalCatalogService;
  @Mock private MatrixResultService matrixResultService;
  @Mock private ResultCalculationService calculationService;

  @Test
  void requiresReasonWhenCorrectingARecordedValue() {
    ApplicationUser analyst = user(RoleCode.ANALYST, "analyst@example.test");
    WorkOrderTest test = workOrderTest(analyst.getId());
    ResultEntry entry = new ResultEntry(test.getId(), null, analyst.getId());
    ServiceOrderSampleTest sourceTest = sourceTest();

    given(userRepository.findByEmailIgnoreCase(analyst.getEmail()))
        .willReturn(Optional.of(analyst));
    given(workOrderService.getRequiredTest(test.getId())).willReturn(test);
    given(sourceTestRepository.findById(test.getSourceSampleTestId()))
        .willReturn(Optional.of(sourceTest));
    given(catalogService.getTest(sourceTest.getTestDefinitionId()))
        .willReturn(new TestDefinition("PH", "pH", ResultDataType.NUMERIC, null));
    given(entryRepository.findByWorkOrderTestId(test.getId())).willReturn(Optional.of(entry));
    mockOperationalWorkOrder(test);

    assertThatThrownBy(
            () ->
                service()
                    .record(
                        test.getId(),
                        new ResultDtos.RecordResultRequest(
                            "Lectura repetida",
                            null,
                            List.of(
                                new ResultDtos.ValueRequest(
                                    "resultado",
                                    "Resultado",
                                    0,
                                    null,
                                    new BigDecimal("11.20"),
                                    null)),
                            null),
                        analyst.getEmail()))
        .isInstanceOf(BusinessRuleViolationException.class)
        .hasMessage("Debe registrar el motivo al corregir un resultado previamente ingresado.");

    verify(changeLogRepository, never()).save(org.mockito.ArgumentMatchers.any());
    assertThat(entry.getRecordedBy()).isEqualTo(analyst.getId());
  }

  @Test
  void preventsTheRecordingUserFromValidatingTheirOwnResult() {
    ApplicationUser validator = user(RoleCode.VALIDATOR, "validator@example.test");
    WorkOrderTest test = workOrderTest(validator.getId());
    ResultEntry entry = new ResultEntry(test.getId(), "Registro inicial", validator.getId());

    given(userRepository.findByEmailIgnoreCase(validator.getEmail()))
        .willReturn(Optional.of(validator));
    given(workOrderService.getRequiredTest(test.getId())).willReturn(test);
    given(entryRepository.findByWorkOrderTestId(test.getId())).willReturn(Optional.of(entry));
    mockOperationalWorkOrder(test);

    assertThatThrownBy(
            () ->
                service()
                    .validate(
                        test.getId(),
                        new ResultDtos.ValidateResultRequest("Revisión técnica"),
                        validator.getEmail()))
        .isInstanceOf(BusinessRuleViolationException.class)
        .hasMessage("La persona que ingresó el resultado no puede validarlo.");

    verify(validationRepository, never()).save(org.mockito.ArgumentMatchers.any());
  }

  @Test
  void returnsAResultToTheAnalystWithAnAuditedReason() {
    ApplicationUser analyst = user(RoleCode.ANALYST, "analyst@example.test");
    ApplicationUser validator = user(RoleCode.VALIDATOR, "validator@example.test");
    WorkOrderTest test = workOrderTest(analyst.getId());
    ResultEntry entry = new ResultEntry(test.getId(), "Registro inicial", analyst.getId());
    ServiceOrderSampleTest sourceTest = sourceTest();

    given(userRepository.findByEmailIgnoreCase(validator.getEmail()))
        .willReturn(Optional.of(validator));
    given(workOrderService.getRequiredTest(test.getId())).willReturn(test);
    given(entryRepository.findByWorkOrderTestId(test.getId())).willReturn(Optional.of(entry));
    given(sourceTestRepository.findById(test.getSourceSampleTestId()))
        .willReturn(Optional.of(sourceTest));
    given(catalogService.getTest(sourceTest.getTestDefinitionId()))
        .willReturn(new TestDefinition("PH", "pH", ResultDataType.NUMERIC, null));
    mockOperationalWorkOrder(test);

    service()
        .reject(
            test.getId(),
            new ResultDtos.RejectResultRequest("Repetir la lectura por deriva del equipo."),
            validator.getEmail());

    assertThat(entry.getStatus()).isEqualTo(ResultEntryStatus.CORRECTION_REQUESTED);
    assertThat(test.getStatus())
        .isEqualTo(com.labcore.platform.workorders.WorkOrderTestStatus.CORRECTION_REQUESTED);
    assertThat(entry.getCorrectionRequestReason())
        .isEqualTo("Repetir la lectura por deriva del equipo.");
    ArgumentCaptor<ResultReviewHistory> historyCaptor =
        ArgumentCaptor.forClass(ResultReviewHistory.class);
    verify(reviewHistoryRepository).save(historyCaptor.capture());
    assertThat(historyCaptor.getValue().getAction())
        .isEqualTo(ResultReviewAction.CORRECTION_REQUESTED);
    assertThat(historyCaptor.getValue().getPerformedByRole()).isEqualTo("VALIDATOR");
  }

  @Test
  void validatorCanCorrectAScalarValueAndValidateANewRevision() {
    ApplicationUser analyst = user(RoleCode.ANALYST, "analyst@example.test");
    ApplicationUser validator = user(RoleCode.VALIDATOR, "validator@example.test");
    WorkOrderTest test = workOrderTest(analyst.getId());
    ResultEntry entry = new ResultEntry(test.getId(), "Registro inicial", analyst.getId());
    ResultValue storedValue =
        new ResultValue(entry.getId(), "resultado", "Resultado", 1, null, BigDecimal.TEN, null);
    ServiceOrderSampleTest sourceTest = sourceTest();

    given(userRepository.findByEmailIgnoreCase(validator.getEmail()))
        .willReturn(Optional.of(validator));
    given(workOrderService.getRequiredTest(test.getId())).willReturn(test);
    given(entryRepository.findByWorkOrderTestId(test.getId())).willReturn(Optional.of(entry));
    given(valueRepository.findByResultEntryIdOrderByPositionAsc(entry.getId()))
        .willReturn(List.of(storedValue));
    given(sourceTestRepository.findById(test.getSourceSampleTestId()))
        .willReturn(Optional.of(sourceTest));
    given(catalogService.getTest(sourceTest.getTestDefinitionId()))
        .willReturn(new TestDefinition("PH", "pH", ResultDataType.NUMERIC, null));
    given(
            calculationService.prepare(
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyList()))
        .willAnswer(
            invocation ->
                new ResultCalculationService.PreparedResult(invocation.getArgument(1), List.of()));
    mockOperationalWorkOrder(test);

    service()
        .correctAndValidate(
            test.getId(),
            new ResultDtos.CorrectAndValidateResultRequest(
                "Se corrigió la transcripción contra la hoja de trabajo.",
                "Dato conforme después de la corrección.",
                "Registro revisado",
                List.of(
                    new ResultDtos.ValueRequest(
                        "resultado", "Resultado", 0, null, new BigDecimal("11.20"), null)),
                null),
            validator.getEmail());

    assertThat(entry.getStatus()).isEqualTo(ResultEntryStatus.VALIDATED);
    assertThat(entry.getResultRevision()).isEqualTo(2);
    assertThat(test.getStatus())
        .isEqualTo(com.labcore.platform.workorders.WorkOrderTestStatus.VALIDATED);
    verify(changeLogRepository).save(org.mockito.ArgumentMatchers.any(ResultChangeLog.class));
    ArgumentCaptor<ResultValidation> validationCaptor =
        ArgumentCaptor.forClass(ResultValidation.class);
    verify(validationRepository).save(validationCaptor.capture());
    assertThat(validationCaptor.getValue().getResultRevision()).isEqualTo(2);
    assertThat(validationCaptor.getValue().getValidatorRole()).isEqualTo("VALIDATOR");
  }

  private void mockOperationalWorkOrder(WorkOrderTest test) {
    WorkOrder workOrder =
        new WorkOrder(
            "OT-2026-0100",
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID(),
            Instant.now().plusSeconds(86_400));
    WorkOrderSample sample =
        new WorkOrderSample(
            workOrder.getId(), UUID.randomUUID(), UUID.randomUUID(), "AG-2026-0001", 1);
    given(workOrderSampleRepository.findById(test.getWorkOrderSampleId()))
        .willReturn(Optional.of(sample));
    given(workOrderService.getRequired(workOrder.getId())).willReturn(workOrder);
  }

  private WorkOrderTest workOrderTest(UUID analystId) {
    return new WorkOrderTest(UUID.randomUUID(), UUID.randomUUID(), analystId, UUID.randomUUID());
  }

  private ServiceOrderSampleTest sourceTest() {
    return new ServiceOrderSampleTest(
        UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
  }

  private ApplicationUser user(RoleCode role, String email) {
    return new ApplicationUser(
        "Usuario de resultados", email, "hash", Set.of(new ApplicationRole(role, role.name())));
  }

  private ResultService service() {
    return new ResultService(
        entryRepository,
        valueRepository,
        validationRepository,
        changeLogRepository,
        reviewHistoryRepository,
        workOrderService,
        workOrderSampleRepository,
        sourceTestRepository,
        userRepository,
        catalogService,
        technicalCatalogService,
        matrixResultService,
        calculationService);
  }
}
