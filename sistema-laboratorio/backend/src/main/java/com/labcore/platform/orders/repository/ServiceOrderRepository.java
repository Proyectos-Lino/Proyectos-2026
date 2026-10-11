package com.labcore.platform.orders;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface ServiceOrderRepository
    extends JpaRepository<ServiceOrder, UUID>, JpaSpecificationExecutor<ServiceOrder> {
  Page<ServiceOrder> findAllByOrderByCreatedAtDesc(Pageable pageable);

  Page<ServiceOrder> findByClientIdOrderByCreatedAtDesc(UUID clientId, Pageable pageable);

  List<ServiceOrder> findByStatusOrderByPlannedDeliveryAtAsc(ServiceOrderStatus status);

  long countByStatus(ServiceOrderStatus status);

  List<ServiceOrder> findTop8ByStatusInAndPlannedDeliveryAtBeforeOrderByPlannedDeliveryAtAsc(
      Collection<ServiceOrderStatus> statuses, Instant dueBefore);
}
