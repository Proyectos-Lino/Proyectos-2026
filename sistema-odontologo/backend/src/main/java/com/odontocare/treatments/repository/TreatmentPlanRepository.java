package com.odontocare.treatments.repository;

import com.odontocare.treatments.model.TreatmentPlan;
import jakarta.persistence.LockModeType;
import java.util.*;
import org.springframework.data.jpa.repository.*;

public interface TreatmentPlanRepository
    extends JpaRepository<TreatmentPlan, UUID>, JpaSpecificationExecutor<TreatmentPlan> {
  @Query("select p.patientId from TreatmentPlan p where p.id=:id")
  Optional<UUID> patientId(UUID id);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select p from TreatmentPlan p where p.id=:id")
  Optional<TreatmentPlan> lockById(UUID id);
}
