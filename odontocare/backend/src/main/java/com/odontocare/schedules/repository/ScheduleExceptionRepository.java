package com.odontocare.schedules.repository;

import com.odontocare.schedules.model.ScheduleException;
import java.util.UUID;
import org.springframework.data.jpa.repository.*;

public interface ScheduleExceptionRepository
    extends JpaRepository<ScheduleException, UUID>, JpaSpecificationExecutor<ScheduleException> {}
