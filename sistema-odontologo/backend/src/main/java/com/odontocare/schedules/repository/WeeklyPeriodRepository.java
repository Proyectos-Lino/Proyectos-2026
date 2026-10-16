package com.odontocare.schedules.repository;

import com.odontocare.schedules.model.WeeklyPeriod;
import java.util.*;
import org.springframework.data.jpa.repository.*;

public interface WeeklyPeriodRepository
    extends JpaRepository<WeeklyPeriod, UUID>, JpaSpecificationExecutor<WeeklyPeriod> {
  List<WeeklyPeriod> findByDentistIdAndDayOfWeekAndActiveTrue(UUID dentistId, int dayOfWeek);
}
