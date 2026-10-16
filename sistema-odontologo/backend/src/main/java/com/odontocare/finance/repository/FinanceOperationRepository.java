package com.odontocare.finance.repository;

import com.odontocare.finance.model.FinanceOperation;
import java.util.*;
import org.springframework.data.jpa.repository.*;

public interface FinanceOperationRepository
    extends JpaRepository<FinanceOperation, UUID>, JpaSpecificationExecutor<FinanceOperation> {
  Optional<FinanceOperation> findByRequestKey(UUID key);
}
