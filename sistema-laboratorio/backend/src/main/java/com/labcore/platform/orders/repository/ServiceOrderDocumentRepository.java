package com.labcore.platform.orders;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ServiceOrderDocumentRepository extends JpaRepository<ServiceOrderDocument, UUID> {
  Optional<ServiceOrderDocument> findFirstByServiceOrderIdOrderByVersionDesc(UUID serviceOrderId);
}
