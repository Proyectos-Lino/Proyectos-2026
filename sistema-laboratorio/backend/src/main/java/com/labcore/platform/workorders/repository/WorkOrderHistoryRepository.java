package com.labcore.platform.workorders;

import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WorkOrderHistoryRepository extends JpaRepository<WorkOrderHistory, UUID> {
  Page<WorkOrderHistory> findByWorkOrderIdOrderByOccurredAtDesc(
      UUID workOrderId, Pageable pageable);
}
