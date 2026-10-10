package com.labcore.platform.orders;

import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ServiceOrderHistoryRepository extends JpaRepository<ServiceOrderHistory, UUID> {
  Page<ServiceOrderHistory> findByServiceOrderIdOrderByOccurredAtDesc(
      UUID serviceOrderId, Pageable pageable);
}
