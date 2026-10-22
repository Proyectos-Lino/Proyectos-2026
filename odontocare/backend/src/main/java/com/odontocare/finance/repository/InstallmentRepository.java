package com.odontocare.finance.repository;

import com.odontocare.finance.model.Installment;
import java.util.*;
import org.springframework.data.jpa.repository.*;

public interface InstallmentRepository
    extends JpaRepository<Installment, UUID>, JpaSpecificationExecutor<Installment> {
  List<Installment> findByScheduleIdOrderByPosition(UUID id);
}
