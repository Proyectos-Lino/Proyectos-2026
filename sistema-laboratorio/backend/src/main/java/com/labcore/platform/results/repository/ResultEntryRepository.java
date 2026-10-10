package com.labcore.platform.results;

import com.labcore.platform.workorders.WorkOrderStatus;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ResultEntryRepository extends JpaRepository<ResultEntry, UUID> {
  Optional<ResultEntry> findByWorkOrderTestId(UUID workOrderTestId);

  List<ResultEntry> findByWorkOrderTestIdIn(Collection<UUID> workOrderTestIds);

  Page<ResultEntry> findByStatusOrderByRecordedAtAsc(ResultEntryStatus status, Pageable pageable);

  Page<ResultEntry> findByStatusInAndRecordedByNotOrderByRecordedAtAsc(
      Collection<ResultEntryStatus> statuses, UUID recordedBy, Pageable pageable);

  @Query(
      """
      select entry
      from ResultEntry entry
      join WorkOrderTest test on test.id = entry.workOrderTestId
      join WorkOrderSample sample on sample.id = test.workOrderSampleId
      join WorkOrder workOrder on workOrder.id = sample.workOrderId
      where entry.status in :statuses
        and entry.recordedBy <> :recordedBy
        and workOrder.status not in :excludedWorkOrderStatuses
      order by entry.recordedAt asc
      """)
  Page<ResultEntry> findOperationalValidationQueue(
      @Param("statuses") Collection<ResultEntryStatus> statuses,
      @Param("recordedBy") UUID recordedBy,
      @Param("excludedWorkOrderStatuses") Collection<WorkOrderStatus> excludedWorkOrderStatuses,
      Pageable pageable);

  long countByStatus(ResultEntryStatus status);

  long countByStatusIn(Collection<ResultEntryStatus> statuses);
}
