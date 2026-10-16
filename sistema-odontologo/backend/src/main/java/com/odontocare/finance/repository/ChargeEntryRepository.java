package com.odontocare.finance.repository;

import com.odontocare.finance.model.ChargeEntry;
import java.math.BigDecimal;
import java.util.*;
import org.springframework.data.jpa.repository.*;

public interface ChargeEntryRepository
    extends JpaRepository<ChargeEntry, UUID>, JpaSpecificationExecutor<ChargeEntry> {
  Optional<ChargeEntry> findBySourceKey(String key);

  List<ChargeEntry> findByEncounterIdAndOriginalIdIsNull(UUID encounterId);

  @Query("select coalesce(sum(c.amount),0) from ChargeEntry c where c.planId=:id")
  BigDecimal planDebt(UUID id);

  @Query("select coalesce(sum(c.amount),0) from ChargeEntry c where c.itemId=:id")
  BigDecimal itemDebt(UUID id);

  @Query("select coalesce(sum(c.amount),0) from ChargeEntry c where c.id=:id or c.originalId=:id")
  BigDecimal originDebt(UUID id);

  @Query("select distinct c.currency from ChargeEntry c where c.patientId=:id order by c.currency")
  List<String> currencies(UUID id);

  @Query(
      "select coalesce(sum(c.amount),0) from ChargeEntry c where c.patientId=:id and"
          + " c.currency=:currency and c.originalId is null")
  BigDecimal charges(UUID id, String currency);

  @Query(
      "select coalesce(sum(c.amount),0) from ChargeEntry c where c.patientId=:id and"
          + " c.currency=:currency and c.originalId is not null")
  BigDecimal adjustments(UUID id, String currency);
}
