package com.labcore.platform.workorders;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.labcore.platform.catalog.CatalogService;
import com.labcore.platform.catalog.LaboratoryArea;
import com.labcore.platform.catalog.LaboratoryService;
import com.labcore.platform.catalog.ResultMode;
import com.labcore.platform.catalog.SampleType;
import com.labcore.platform.orders.DeliveryMethod;
import com.labcore.platform.orders.DocumentCodeGenerator;
import com.labcore.platform.orders.PostAnalysisCustody;
import com.labcore.platform.orders.ServiceOrder;
import com.labcore.platform.orders.ServiceOrderRepository;
import com.labcore.platform.orders.ServiceOrderSample;
import com.labcore.platform.orders.ServiceOrderSampleRepository;
import com.labcore.platform.orders.ServiceOrderSampleTest;
import com.labcore.platform.orders.ServiceOrderSampleTestRepository;
import com.labcore.platform.orders.ServiceOrderService;
import com.labcore.platform.security.ApplicationRole;
import com.labcore.platform.security.ApplicationUser;
import com.labcore.platform.security.ApplicationUserRepository;
import com.labcore.platform.security.RoleCode;
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
class WorkOrderServiceTest {

  @Mock private WorkOrderRepository workOrderRepository;
  @Mock private WorkOrderSampleRepository workOrderSampleRepository;
  @Mock private WorkOrderTestRepository workOrderTestRepository;
  @Mock private WorkOrderHistoryRepository historyRepository;
  @Mock private ServiceOrderRepository serviceOrderRepository;
  @Mock private ServiceOrderService serviceOrderService;
  @Mock private ServiceOrderSampleTestRepository requestedTestRepository;
  @Mock private ServiceOrderSampleRepository sourceSampleRepository;
  @Mock private TechnicalSampleRepository technicalSampleRepository;
  @Mock private ApplicationUserRepository userRepository;
  @Mock private CatalogService catalogService;
  @Mock private SampleCodeGenerator sampleCodeGenerator;
  @Mock private DocumentCodeGenerator documentCodeGenerator;
  @Mock private WorkOrderDocumentService documentService;

  @Test
  void rejectsLaboratoryDueDateAfterCustomerCommitment() {
    ServiceOrder sourceOrder = sourceOrder();
    ApplicationUser manager = technicalManager();
    String managerEmail = manager.getEmail();
    given(userRepository.findByEmailIgnoreCase(managerEmail)).willReturn(Optional.of(manager));
    given(serviceOrderService.getRequired(sourceOrder.getId())).willReturn(sourceOrder);

    assertThatThrownBy(
            () ->
                service()
                    .create(
                        new WorkOrderDtos.CreateWorkOrderRequest(
                            sourceOrder.getId(),
                            UUID.randomUUID(),
                            UUID.randomUUID(),
                            sourceOrder.getPlannedDeliveryAt().plusSeconds(60),
                            List.of(
                                new WorkOrderDtos.SampleClassificationRequest(
                                    UUID.randomUUID(), UUID.randomUUID())),
                            List.of(
                                new WorkOrderDtos.AnalystAssignmentRequest(
                                    UUID.randomUUID(), UUID.randomUUID()))),
                        managerEmail))
        .isInstanceOf(com.labcore.platform.common.exception.BusinessRuleViolationException.class)
        .hasMessage(
            "La fecha interna de laboratorio no puede ser posterior a la entrega prometida al cliente.");
  }

  @Test
  void recordsCancellationDataOnWorkOrder() {
    WorkOrder workOrder =
        new WorkOrder(
            "OT-2026-0001",
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID(),
            Instant.now().plusSeconds(86_400));

    workOrder.cancel("La muestra no cumplió las condiciones de recepción.");

    assertThat(workOrder.getStatus()).isEqualTo(WorkOrderStatus.CANCELLED);
    assertThat(workOrder.getCancellationReason())
        .isEqualTo("La muestra no cumplió las condiciones de recepción.");
    assertThat(workOrder.getCancelledAt()).isNotNull();
  }

  @Test
  void preventsChangingAClassificationThatAlreadyHasAnInternalCode() {
    ApplicationUser administrator = administrator();
    ServiceOrder sourceOrder = sourceOrder();
    LaboratoryArea area = new LaboratoryArea("FQ", "Fisicoquímica");
    LaboratoryService laboratoryService = new LaboratoryService("ANA", "Análisis", area.getId());
    SampleType declaredType = new SampleType("ALI", "Alimento", "ALI", ResultMode.SCALAR);
    SampleType differentType = new SampleType("SEM", "Semilla", "SEM", ResultMode.SCALAR);
    ServiceOrderSample sourceSample = sourceSample(sourceOrder.getId(), declaredType.getId());
    ServiceOrderSampleTest sourceTest =
        new ServiceOrderSampleTest(
            sourceSample.getId(), laboratoryService.getId(), UUID.randomUUID(), UUID.randomUUID());
    TechnicalSample technicalSample =
        new TechnicalSample(
            sourceSample.getId(),
            declaredType.getId(),
            declaredType.getId(),
            "ALI-2026-0001",
            1,
            administrator.getEmail());

    given(userRepository.findByEmailIgnoreCase(administrator.getEmail()))
        .willReturn(Optional.of(administrator));
    given(userRepository.findById(administrator.getId())).willReturn(Optional.of(administrator));
    given(serviceOrderService.getRequired(sourceOrder.getId())).willReturn(sourceOrder);
    given(catalogService.getArea(area.getId())).willReturn(area);
    given(catalogService.getService(laboratoryService.getId())).willReturn(laboratoryService);
    given(requestedTestRepository.findAllByIdForUpdate(List.of(sourceTest.getId())))
        .willReturn(List.of(sourceTest));
    given(sourceSampleRepository.findAllByIdForUpdate(List.of(sourceSample.getId())))
        .willReturn(List.of(sourceSample));
    given(technicalSampleRepository.findByServiceOrderSampleIdIn(Set.of(sourceSample.getId())))
        .willReturn(List.of(technicalSample));

    WorkOrderDtos.CreateWorkOrderRequest request =
        new WorkOrderDtos.CreateWorkOrderRequest(
            sourceOrder.getId(),
            area.getId(),
            administrator.getId(),
            sourceOrder.getPlannedDeliveryAt().minusSeconds(60),
            List.of(
                new WorkOrderDtos.SampleClassificationRequest(
                    sourceSample.getId(), differentType.getId())),
            List.of(
                new WorkOrderDtos.AnalystAssignmentRequest(
                    sourceTest.getId(), administrator.getId())));

    assertThatThrownBy(() -> service().create(request, administrator.getEmail()))
        .isInstanceOf(com.labcore.platform.common.exception.BusinessRuleViolationException.class)
        .hasMessage(
            "La muestra ya tiene una clasificación técnica y un código interno definitivos.");

    verify(sampleCodeGenerator, never()).next(org.mockito.ArgumentMatchers.any());
    verify(workOrderRepository, never()).save(org.mockito.ArgumentMatchers.any());
  }

  @Test
  void releasesPendingSourceTestsWhenCancellingAnOrder() {
    ApplicationUser manager = technicalManager();
    WorkOrder workOrder =
        new WorkOrder(
            "OT-2026-0002",
            UUID.randomUUID(),
            UUID.randomUUID(),
            manager.getId(),
            Instant.now().plusSeconds(86_400));
    WorkOrderSample sample =
        new WorkOrderSample(
            workOrder.getId(), UUID.randomUUID(), UUID.randomUUID(), "AG-2026-0001", 1);
    ServiceOrderSampleTest sourceTest =
        new ServiceOrderSampleTest(
            sample.getServiceOrderSampleId(),
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID());
    sourceTest.markAssigned();
    WorkOrderTest workOrderTest =
        new WorkOrderTest(
            sample.getId(), sourceTest.getId(), UUID.randomUUID(), sourceTest.getTestMethodId());
    given(userRepository.findByEmailIgnoreCase(manager.getEmail()))
        .willReturn(Optional.of(manager));
    given(workOrderRepository.findById(workOrder.getId())).willReturn(Optional.of(workOrder));
    given(workOrderSampleRepository.findByWorkOrderId(workOrder.getId()))
        .willReturn(List.of(sample));
    given(workOrderTestRepository.findByWorkOrderSampleIdIn(List.of(sample.getId())))
        .willReturn(List.of(workOrderTest));
    given(requestedTestRepository.findAllById(List.of(sourceTest.getId())))
        .willReturn(List.of(sourceTest));

    service()
        .cancel(
            workOrder.getId(),
            new WorkOrderDtos.CancelWorkOrderRequest("Recepción no conforme"),
            manager.getEmail());

    assertThat(workOrder.getStatus()).isEqualTo(WorkOrderStatus.CANCELLED);
    assertThat(sourceTest.getStatus())
        .isEqualTo(com.labcore.platform.orders.RequestedTestStatus.PENDING);
  }

  @Test
  void recordsTheSpecificResultEventAndItsProgress() {
    WorkOrder workOrder =
        new WorkOrder(
            "OT-2026-0003",
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID(),
            Instant.now().plusSeconds(86_400));
    WorkOrderSample sample =
        new WorkOrderSample(
            workOrder.getId(), UUID.randomUUID(), UUID.randomUUID(), "AG-2026-0002", 2);
    WorkOrderTest recordedTest =
        new WorkOrderTest(sample.getId(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
    WorkOrderTest pendingTest =
        new WorkOrderTest(sample.getId(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
    recordedTest.markRecorded();
    given(workOrderRepository.findById(workOrder.getId())).willReturn(Optional.of(workOrder));
    given(workOrderSampleRepository.findByWorkOrderId(workOrder.getId()))
        .willReturn(List.of(sample));
    given(workOrderTestRepository.findByWorkOrderSampleIdIn(List.of(sample.getId())))
        .willReturn(List.of(recordedTest, pendingTest));

    service()
        .synchronizeStatus(
            workOrder.getId(),
            "analista@example.test",
            WorkOrderHistoryAction.RESULT_RECORDED,
            "Resultado registrado para Sólidos disueltos en la muestra AG-2026-0002.");

    ArgumentCaptor<WorkOrderHistory> historyCaptor =
        ArgumentCaptor.forClass(WorkOrderHistory.class);
    verify(historyRepository).save(historyCaptor.capture());
    WorkOrderHistory history = historyCaptor.getValue();

    assertThat(workOrder.getStatus()).isEqualTo(WorkOrderStatus.IN_PROGRESS);
    assertThat(history.getAction()).isEqualTo(WorkOrderHistoryAction.RESULT_RECORDED);
    assertThat(history.getPreviousStatus()).isEqualTo(WorkOrderStatus.PENDING);
    assertThat(history.getNewStatus()).isEqualTo(WorkOrderStatus.IN_PROGRESS);
    assertThat(history.getChangeNote())
        .contains("Sólidos disueltos", "AG-2026-0002", "1 de 2", "0 de 2");
  }

  private ServiceOrder sourceOrder() {
    ServiceOrder order =
        new ServiceOrder(
            "OS-2026-0010",
            UUID.randomUUID(),
            UUID.randomUUID(),
            null,
            null,
            null,
            DeliveryMethod.EMAIL,
            null,
            Instant.now().plusSeconds(86_400),
            null,
            null,
            null,
            false,
            PostAnalysisCustody.RETAINED,
            null);
    order.confirmForLaboratory();
    return order;
  }

  private ApplicationUser technicalManager() {
    return new ApplicationUser(
        "Responsable técnico",
        "responsable@example.test",
        "hash",
        Set.of(new ApplicationRole(RoleCode.TECHNICAL_MANAGER, "Responsable técnico")));
  }

  private ApplicationUser administrator() {
    return new ApplicationUser(
        "Administrador",
        "admin@example.test",
        "hash",
        Set.of(new ApplicationRole(RoleCode.ADMIN, "Administrador")));
  }

  private ServiceOrderSample sourceSample(UUID serviceOrderId, UUID sampleTypeId) {
    return new ServiceOrderSample(
        serviceOrderId,
        1,
        "M-CLIENTE-01",
        null,
        "Producto",
        null,
        sampleTypeId,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        false,
        null);
  }

  private WorkOrderService service() {
    return new WorkOrderService(
        workOrderRepository,
        workOrderSampleRepository,
        workOrderTestRepository,
        historyRepository,
        serviceOrderRepository,
        serviceOrderService,
        requestedTestRepository,
        sourceSampleRepository,
        technicalSampleRepository,
        userRepository,
        catalogService,
        sampleCodeGenerator,
        documentCodeGenerator,
        documentService);
  }
}
