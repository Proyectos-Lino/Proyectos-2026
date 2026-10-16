package com.odontocare.treatments.repository;

import com.odontocare.treatments.model.PlanOperation;
import java.util.*;
import org.springframework.data.jpa.repository.*;

public interface PlanOperationRepository
    extends JpaRepository<PlanOperation, UUID>, JpaSpecificationExecutor<PlanOperation> {
  Optional<PlanOperation> findByRequestKey(UUID key);
}
