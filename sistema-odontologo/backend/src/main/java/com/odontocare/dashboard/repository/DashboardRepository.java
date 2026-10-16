package com.odontocare.dashboard.repository;

import com.odontocare.dashboard.dto.DashboardContracts.*;
import com.odontocare.kapso.config.KapsoProperties;
import com.odontocare.reports.dto.ReportContracts.Window;
import java.math.BigDecimal;
import java.sql.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import org.springframework.jdbc.core.namedparam.*;
import org.springframework.stereotype.Repository;

@Repository
public class DashboardRepository {
  private final NamedParameterJdbcTemplate jdbc;
  private final KapsoProperties kapso;

  public DashboardRepository(NamedParameterJdbcTemplate jdbc, KapsoProperties kapso) {
    this.jdbc = jdbc;
    this.kapso = kapso;
  }

  public Appointments appointments(Window day, UUID dentist) {
    var parameters =
        new MapSqlParameterSource()
            .addValue("start", Timestamp.from(day.start()))
            .addValue("end", Timestamp.from(day.end()));
    String where = " WHERE starts_at>=:start AND starts_at<:end";
    if (dentist != null) {
      where += " AND dentist_id=:dentist";
      parameters.addValue("dentist", dentist);
    }
    return jdbc.queryForObject(
        """
        SELECT count(*) total,count(*) FILTER(WHERE status='WAITING') waiting,
          count(*) FILTER(WHERE status='IN_PROGRESS') in_progress,count(*) FILTER(WHERE status='ATTENDED') attended,
          count(*) FILTER(WHERE status='CANCELLED') cancelled,count(*) FILTER(WHERE status='NO_SHOW') no_show
        FROM appointment
        """
            + where,
        parameters,
        (r, i) ->
            new Appointments(
                r.getLong("total"),
                r.getLong("waiting"),
                r.getLong("in_progress"),
                r.getLong("attended"),
                r.getLong("cancelled"),
                r.getLong("no_show")));
  }

  public Conversations conversations() {
    var parameters =
        new MapSqlParameterSource().addValue("provider", kapso.isEnabled() ? "KAPSO" : "TWILIO");
    String join =
        kapso.isEnabled()
            ? " JOIN kapso_conversation k ON k.id=s.conversation_id AND k.phone_number_id=:number"
            : "";
    if (kapso.isEnabled()) parameters.addValue("number", kapso.getPhoneNumberId());
    return jdbc.queryForObject(
        "SELECT count(*) FILTER(WHERE s.mode='HANDOFF') pending,count(*) FILTER(WHERE"
            + " s.mode='HUMAN') human FROM agent_supervision s JOIN agent_conversation_source c ON"
            + " c.id=s.conversation_id"
            + join
            + " WHERE c.provider=:provider",
        parameters,
        (r, i) -> new Conversations(r.getLong("pending"), r.getLong("human")));
  }

  public List<Money> money(Window window, String defaultCurrency) {
    var parameters =
        new MapSqlParameterSource()
            .addValue("from", java.sql.Date.valueOf(window.from()))
            .addValue("to", java.sql.Date.valueOf(window.to()));
    Map<String, Map<String, BigDecimal>> totals = new TreeMap<>();
    Map<String, Long> counts = new HashMap<>();
    jdbc.query(
        """
        SELECT currency,count(*) movements,
          coalesce(sum(amount) FILTER(WHERE kind='PAYMENT'),0) received,
          coalesce(sum(amount) FILTER(WHERE kind IN('REFUND','REVERSAL')),0) returned,
          coalesce(sum(CASE WHEN kind='EXPENSE' THEN amount WHEN kind='EXPENSE_REVERSAL' THEN -amount ELSE 0 END),0) expenses
        FROM money_movement WHERE occurred_on BETWEEN :from AND :to GROUP BY currency ORDER BY currency
        """,
        parameters,
        r -> {
          String currency = r.getString("currency");
          totals.put(
              currency,
              Map.of(
                  "received",
                  r.getBigDecimal("received"),
                  "returned",
                  r.getBigDecimal("returned"),
                  "expenses",
                  r.getBigDecimal("expenses")));
          counts.put(currency, r.getLong("movements"));
        });
    if (totals.isEmpty())
      totals.put(
          defaultCurrency,
          Map.of(
              "received",
              BigDecimal.ZERO,
              "returned",
              BigDecimal.ZERO,
              "expenses",
              BigDecimal.ZERO));
    long days = ChronoUnit.DAYS.between(window.from(), window.to());
    String bucket = days <= 30 ? "day" : days <= 92 ? "week" : "month";
    parameters.addValue("bucket", bucket);
    var result = new ArrayList<Money>();
    for (var entry : totals.entrySet()) {
      String currency = entry.getKey();
      parameters.addValue("currency", currency);
      var trend =
          jdbc.query(
              """
              WITH buckets AS(SELECT generate_series(date_trunc(:bucket,CAST(:from AS timestamp)),date_trunc(:bucket,CAST(:to AS timestamp)),
                CASE :bucket WHEN 'day' THEN interval '1 day' WHEN 'week' THEN interval '1 week' ELSE interval '1 month' END)::date bucket_date),
              money AS(SELECT date_trunc(:bucket,occurred_on::timestamp)::date bucket_date,
                sum(CASE WHEN kind='PAYMENT' THEN amount WHEN kind IN('REFUND','REVERSAL') THEN -amount ELSE 0 END) collections,
                sum(CASE WHEN kind='EXPENSE' THEN amount WHEN kind='EXPENSE_REVERSAL' THEN -amount ELSE 0 END) expenses
                FROM money_movement WHERE occurred_on BETWEEN :from AND :to AND currency=:currency GROUP BY bucket_date)
              SELECT b.bucket_date,coalesce(m.collections,0) collections,coalesce(m.expenses,0) expenses FROM buckets b LEFT JOIN money m ON m.bucket_date=b.bucket_date ORDER BY b.bucket_date
              """,
              parameters,
              (r, i) ->
                  new Point(
                      r.getDate("bucket_date").toLocalDate(),
                      r.getBigDecimal("collections").toPlainString(),
                      r.getBigDecimal("expenses").toPlainString()));
      var data = entry.getValue();
      var collected = data.get("received").subtract(data.get("returned"));
      result.add(
          new Money(
              currency,
              data.get("received").toPlainString(),
              data.get("returned").toPlainString(),
              collected.toPlainString(),
              data.get("expenses").toPlainString(),
              collected.subtract(data.get("expenses")).toPlainString(),
              counts.getOrDefault(currency, 0L),
              trend,
              bucket.toUpperCase(Locale.ROOT)));
    }
    return List.copyOf(result);
  }
}
