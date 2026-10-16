package com.odontocare.finance.repository;

import java.math.BigDecimal;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class ChargeStateRepository {
  private final JdbcTemplate jdbc;

  public ChargeStateRepository(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public boolean closed(UUID id) {
    return Boolean.TRUE.equals(
        jdbc.queryForObject(
            "select exists(select 1 from charge_state where charge_id=? and closed)",
            Boolean.class,
            id));
  }

  public void setClosed(UUID id, boolean closed) {
    jdbc.update(
        "insert into charge_state(charge_id,closed) values (?,?) on conflict(charge_id) do update"
            + " set closed=excluded.closed,changed_at=now()",
        id,
        closed);
  }

  public BigDecimal activeTotal(UUID patient, String currency, boolean applied) {
    return jdbc.queryForObject(
        "select coalesce(sum("
            + (applied ? "applied" : "debt")
            + "),0) from financial_charge_balance where patient_id=? and currency=? and not closed",
        BigDecimal.class,
        patient,
        currency);
  }
}
