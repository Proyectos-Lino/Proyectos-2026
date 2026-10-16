package com.odontocare.finance.repository;

import com.odontocare.finance.model.FinancialDocument;
import java.util.*;
import org.springframework.data.jpa.repository.*;

public interface FinancialDocumentRepository
    extends JpaRepository<FinancialDocument, UUID>, JpaSpecificationExecutor<FinancialDocument> {
  boolean existsByFileNameAndGeneratedTrue(String fileName);

  Optional<FinancialDocument> findByCashSessionIdAndGeneratedTrue(UUID id);

  Optional<FinancialDocument> findByMovementIdAndGeneratedTrue(UUID id);
}
