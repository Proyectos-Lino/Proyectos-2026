package com.odontocare.finance.repository;

import com.odontocare.finance.model.MoneyMovement;
import java.math.BigDecimal;
import java.util.*;
import org.springframework.data.jpa.repository.*;

public interface MoneyMovementRepository
    extends JpaRepository<MoneyMovement, UUID>, JpaSpecificationExecutor<MoneyMovement> {
  Optional<MoneyMovement> findByReceiptCode(String code);

  @Query("select coalesce(sum(m.amount),0) from MoneyMovement m where m.originalId=:id")
  BigDecimal returned(UUID id);

  @Query(
      "select coalesce(sum(case when m.kind='PAYMENT' then m.amount else -m.amount end),0) from"
          + " MoneyMovement m where m.patientId=:patient and m.currency=:currency")
  BigDecimal received(UUID patient, String currency);

  @Query("select distinct m.currency from MoneyMovement m where m.patientId=:patient")
  List<String> currencies(UUID patient);

  @Query(
      "select coalesce(sum(case when m.kind in('PAYMENT','EXPENSE_REVERSAL') then m.amount else"
          + " -m.amount end),0) from MoneyMovement m where m.cashSessionId=:id and m.method='CASH'")
  BigDecimal cashNet(UUID id);

  @Query(
      "select coalesce(sum(case when m.kind in('PAYMENT','EXPENSE_REVERSAL') then m.amount else"
          + " -m.amount end),0) from MoneyMovement m where m.cashSessionId=:id and"
          + " m.method<>'CASH'")
  BigDecimal nonCashNet(UUID id);
}
