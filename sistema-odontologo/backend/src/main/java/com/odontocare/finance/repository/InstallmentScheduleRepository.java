package com.odontocare.finance.repository;

import com.odontocare.finance.model.InstallmentSchedule;
import java.util.*;
import org.springframework.data.jpa.repository.*;

public interface InstallmentScheduleRepository
    extends JpaRepository<InstallmentSchedule, UUID>,
        JpaSpecificationExecutor<InstallmentSchedule> {
  Optional<InstallmentSchedule> findByChargeIdAndActiveTrue(UUID id);
}
