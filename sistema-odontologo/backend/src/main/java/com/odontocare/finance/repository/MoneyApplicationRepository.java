package com.odontocare.finance.repository;

import com.odontocare.finance.model.MoneyApplication;
import java.math.BigDecimal;
import java.util.*;
import org.springframework.data.jpa.repository.*;

public interface MoneyApplicationRepository
    extends JpaRepository<MoneyApplication, UUID>, JpaSpecificationExecutor<MoneyApplication> {
  @Query("select coalesce(sum(a.amount),0) from MoneyApplication a where a.paymentId=:id")
  BigDecimal paymentApplied(UUID id);

  @Query(
      "select coalesce(sum(a.amount),0) from MoneyApplication a, ChargeBalance c where"
          + " a.paymentId=:id and a.chargeId=c.id and c.closed=true")
  BigDecimal closedApplied(UUID id);

  @Query("select coalesce(sum(a.amount),0) from MoneyApplication a where a.chargeId=:id")
  BigDecimal chargeApplied(UUID id);

  @Query(
      "select coalesce(sum(a.amount),0) from MoneyApplication a, ChargeEntry c where"
          + " a.chargeId=c.id and a.patientId=:patient and c.currency=:currency")
  BigDecimal applied(UUID patient, String currency);

  @Query(
      "select a.chargeId from MoneyApplication a where a.paymentId=:id group by a.chargeId having"
          + " sum(a.amount)>0")
  List<UUID> appliedChargeIds(UUID id);

  @Query(
      "select coalesce(sum(a.amount),0) from MoneyApplication a where a.paymentId=:payment and"
          + " a.chargeId=:charge")
  BigDecimal pairApplied(UUID payment, UUID charge);

  interface PaymentAllocationAmount {
    UUID getPaymentId();

    BigDecimal getAmount();
  }

  @Query(
      "select a.paymentId as paymentId, sum(a.amount) as amount from MoneyApplication a"
          + " where a.chargeId=:charge and a.paymentId in :payments group by a.paymentId")
  List<PaymentAllocationAmount> chargePaymentAmounts(UUID charge, Collection<UUID> payments);
}
