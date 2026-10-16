package com.odontocare.treatments.repository;

import com.odontocare.treatments.model.PlanSession;
import java.util.*;
import org.springframework.data.jpa.repository.*;

public interface PlanSessionRepository
    extends JpaRepository<PlanSession, UUID>, JpaSpecificationExecutor<PlanSession> {
  @Query("select coalesce(sum(s.sessions),0) from PlanSession s where s.itemId=:id")
  long completed(UUID id);

  @Query("select coalesce(sum(s.sessions),0) from PlanSession s where s.planId=:id")
  long total(UUID id);
}
