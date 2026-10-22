package com.odontocare.finance.repository;

import com.odontocare.finance.model.CashSession;
import java.util.*;
import org.springframework.data.jpa.repository.*;

public interface CashSessionRepository
    extends JpaRepository<CashSession, UUID>, JpaSpecificationExecutor<CashSession> {
  Optional<CashSession> findByClosedAtIsNull();
}
