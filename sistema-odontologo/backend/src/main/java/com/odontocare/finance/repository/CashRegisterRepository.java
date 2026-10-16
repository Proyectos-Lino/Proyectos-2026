package com.odontocare.finance.repository;

import com.odontocare.finance.model.CashRegister;
import java.util.*;
import org.springframework.data.jpa.repository.*;

public interface CashRegisterRepository
    extends JpaRepository<CashRegister, Short>, JpaSpecificationExecutor<CashRegister> {
  @Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
  @Query("select r from CashRegister r where r.id=1")
  Optional<CashRegister> lockRegister();
}
