package com.labcore.platform.workorders;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface WorkOrderTestRepository extends JpaRepository<WorkOrderTest, UUID> {
  List<WorkOrderTest> findByWorkOrderSampleIdIn(Collection<UUID> workOrderSampleIds);

  List<WorkOrderTest> findBySourceSampleTestIdIn(Collection<UUID> sourceSampleTestIds);

  List<WorkOrderTest> findByAssignedAnalystId(UUID assignedAnalystId);

  List<WorkOrderTest> findByWorkOrderSampleIdInOrderByCreatedAtAsc(
      Collection<UUID> workOrderSampleIds);

  @Query(
      """
      select test
      from WorkOrderTest test
      join WorkOrderSample sample on sample.id = test.workOrderSampleId
      join WorkOrder workOrder on workOrder.id = sample.workOrderId
      where test.status in :testStatuses
        and workOrder.status not in :excludedWorkOrderStatuses
      order by test.updatedAt desc
      """)
  Page<WorkOrderTest> findOperationalResultQueue(
      @Param("testStatuses") Collection<WorkOrderTestStatus> testStatuses,
      @Param("excludedWorkOrderStatuses") Collection<WorkOrderStatus> excludedWorkOrderStatuses,
      Pageable pageable);

  @Query(
      """
      select test
      from WorkOrderTest test
      join WorkOrderSample sample on sample.id = test.workOrderSampleId
      join WorkOrder workOrder on workOrder.id = sample.workOrderId
      where test.assignedAnalystId = :analystId
        and test.status in :testStatuses
        and workOrder.status not in :excludedWorkOrderStatuses
      order by test.updatedAt desc
      """)
  Page<WorkOrderTest> findOperationalResultQueueForAnalyst(
      @Param("analystId") UUID analystId,
      @Param("testStatuses") Collection<WorkOrderTestStatus> testStatuses,
      @Param("excludedWorkOrderStatuses") Collection<WorkOrderStatus> excludedWorkOrderStatuses,
      Pageable pageable);
}
