package com.labcore.platform.workflow;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.labcore.platform.orders.*;
import com.labcore.platform.reports.*;
import com.labcore.platform.results.*;
import com.labcore.platform.workorders.*;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TechnicalCorrectionServiceTest {
  @Mock WorkOrderService workOrders;
  @Mock WorkOrderSampleRepository samples;
  @Mock WorkOrderTestRepository tests;
  @Mock ResultEntryRepository entries;
  @Mock ResultService results;
  @Mock ServiceOrderService orders;
  @Mock ServiceOrderHistoryRepository orderHistory;
  @Mock LaboratoryReportService reports;
  @Mock CorrectionAuthority authority;
  @Mock OperationVersionService versions;
  @Mock LaboratoryReportRepository reportRepository;
  @Mock WorkOrderRepository workOrderRepository;
  @InjectMocks TechnicalCorrectionService service;

  @Test
  void reopensClosedWorkWithoutDeletingOrInvalidatingItsResults() {
    var order = order();
    order.close();
    prepare(order);
    service.returnWorkOrder(order.getId(), "Revisar cierre", "admin@test.local");
    assertThat(order.getStatus()).isEqualTo(WorkOrderStatus.VALIDATED);
    assertThat(order.getClosedAt()).isNull();
    verifyNoInteractions(results, entries);
    verify(workOrders)
        .captureVersion(order.getId(), "BEFORE_CORRECTION", "Revisar cierre", "admin@test.local");
  }

  @Test
  void reopensValidatedResultsForReviewAndConservesTheirPreviousVersion() {
    var order = order();
    order.markValidated();
    prepare(order);
    var sample =
        new WorkOrderSample(order.getId(), UUID.randomUUID(), UUID.randomUUID(), "AG-1", 1);
    var test =
        new WorkOrderTest(sample.getId(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
    var entry = new ResultEntry(test.getId(), "Observación", UUID.randomUUID());
    entry.validate();
    when(samples.findByWorkOrderId(order.getId())).thenReturn(List.of(sample));
    when(tests.findByWorkOrderSampleIdIn(List.of(sample.getId()))).thenReturn(List.of(test));
    when(entries.findByWorkOrderTestId(test.getId())).thenReturn(Optional.of(entry));
    service.returnWorkOrder(order.getId(), "Revisar aprobación", "admin@test.local");
    assertThat(order.getStatus()).isEqualTo(WorkOrderStatus.RESULTS_RECORDED);
    verify(results)
        .returnForCorrection(test.getId(), "Revisar aprobación", true, "admin@test.local");
    verify(entries, never()).delete(any());
  }

  private WorkOrder order() {
    return new WorkOrder(
        "OT", UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), Instant.now());
  }

  private void prepare(WorkOrder order) {
    when(workOrders.getRequired(order.getId())).thenReturn(order);
    ServiceOrder source = mock(ServiceOrder.class);
    when(source.getId()).thenReturn(order.getServiceOrderId());
    when(source.getStatus()).thenReturn(ServiceOrderStatus.IN_PROGRESS);
    when(orders.getRequired(order.getServiceOrderId())).thenReturn(source);
  }
}
