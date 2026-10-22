package com.odontocare.treatments.repository;

import com.odontocare.treatments.model.PlanItem;
import java.math.BigDecimal;
import java.util.*;
import org.springframework.data.jpa.repository.*;

public interface PlanItemRepository
    extends JpaRepository<PlanItem, UUID>, JpaSpecificationExecutor<PlanItem> {
  List<PlanItem> findByPlanIdOrderByPosition(UUID planId);

  void deleteByPlanId(UUID planId);

  @Query("select coalesce(sum(i.unitPrice*i.quantity),0) from PlanItem i where i.planId=:id")
  BigDecimal total(UUID id);

  @Query("select coalesce(sum(i.sessions),0) from PlanItem i where i.planId=:id")
  long sessions(UUID id);

  @Query("select coalesce(max(i.position),0) from PlanItem i where i.planId=:id")
  int lastPosition(UUID id);
}
