package com.labcore.platform.workorders;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WorkOrderSampleRepository extends JpaRepository<WorkOrderSample, UUID> {
  List<WorkOrderSample> findByWorkOrderId(UUID workOrderId);

  List<WorkOrderSample> findByWorkOrderIdIn(Collection<UUID> workOrderIds);

  List<WorkOrderSample> findByServiceOrderSampleIdIn(Collection<UUID> serviceOrderSampleIds);
}
