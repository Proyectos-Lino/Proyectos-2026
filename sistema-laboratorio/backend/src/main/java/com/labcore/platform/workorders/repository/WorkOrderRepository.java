package com.labcore.platform.workorders;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface WorkOrderRepository
    extends JpaRepository<WorkOrder, UUID>, JpaSpecificationExecutor<WorkOrder> {
  boolean existsByServiceOrderId(UUID serviceOrderId);

  List<WorkOrder> findByServiceOrderIdOrderByCreatedAtAsc(UUID serviceOrderId);

  long countByStatus(WorkOrderStatus status);

  List<WorkOrder> findTop8ByStatusInAndLaboratoryDueAtBeforeOrderByLaboratoryDueAtAsc(
      java.util.Collection<WorkOrderStatus> statuses, Instant dueBefore);
}
