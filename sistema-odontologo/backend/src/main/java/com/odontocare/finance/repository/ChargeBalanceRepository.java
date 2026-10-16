package com.odontocare.finance.repository;

import com.odontocare.finance.model.ChargeBalance;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.*;

public interface ChargeBalanceRepository
    extends JpaRepository<ChargeBalance, UUID>, JpaSpecificationExecutor<ChargeBalance> {
  List<ChargeBalance> findByIdIn(Collection<UUID> ids);
}
