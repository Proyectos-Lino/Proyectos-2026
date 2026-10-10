package com.labcore.platform.workorders;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WorkOrderDocumentRepository extends JpaRepository<WorkOrderDocument, UUID> {
  Optional<WorkOrderDocument> findFirstByWorkOrderIdOrderByVersionDesc(UUID workOrderId);
}
