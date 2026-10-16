package com.odontocare.reports.repository;

import com.odontocare.reports.dto.ReportContracts.*;
import com.odontocare.reports.dto.ReportQuery;
import com.odontocare.reports.model.ReportType;
import com.odontocare.reports.service.ReportDefinitions;
import java.math.BigDecimal;
import java.sql.*;
import java.util.*;
import org.springframework.jdbc.core.namedparam.*;
import org.springframework.stereotype.Repository;

@Repository
public class ReportRepository {
  public record Table(String sql, MapSqlParameterSource parameters, Definition definition) {}

  private record Sum(String column, String label, String kind) {}

  private final NamedParameterJdbcTemplate jdbc;
  private final OperationalReportSql operational;
  private final FinancialReportSql financial;
  private final ReportDefinitions definitions;

  public ReportRepository(
      NamedParameterJdbcTemplate jdbc,
      OperationalReportSql operational,
      FinancialReportSql financial,
      ReportDefinitions definitions) {
    this.jdbc = jdbc;
    this.operational = operational;
    this.financial = financial;
    this.definitions = definitions;
  }

  public Table table(ReportType type, ReportQuery query, Window window) {
    String source =
        switch (type) {
          case APPOINTMENTS -> operational.appointments(false);
          case ABSENCES -> operational.appointments(true);
          case TREATMENTS -> operational.treatments();
          case SERVICES -> operational.services();
          case COLLECTIONS -> financial.money(false);
          case EXPENSES -> financial.money(true);
          case CHARGES -> financial.charges();
          case BALANCES -> financial.balances();
          case INSTALLMENTS ->
              query.isIncludeEarlier()
                  ? financial.installments(false) + " WHERE d.due_on<=:to"
                  : financial.installments(true);
        };
    return filtered(source, definitions.get(type), query, window);
  }

  public Table overdue(ReportQuery query, Window window) {
    return filtered(
        financial.installments(false), definitions.get(ReportType.INSTALLMENTS), query, window);
  }

  private Table filtered(String source, Definition definition, ReportQuery query, Window window) {
    var parameters =
        new MapSqlParameterSource()
            .addValue("from", java.sql.Date.valueOf(window.from()))
            .addValue("to", java.sql.Date.valueOf(window.to()))
            .addValue("startAt", Timestamp.from(window.start()))
            .addValue("endAt", Timestamp.from(window.end()))
            .addValue("zone", window.zone().getId());
    StringBuilder where =
        new StringBuilder("SELECT * FROM (").append(source).append(") q WHERE 1=1");
    if (query.getDentistId() != null) {
      where.append(" AND q.\"dentistId\"=:dentist");
      parameters.addValue("dentist", query.getDentistId());
    }
    if (query.getPatientId() != null) {
      where.append(" AND q.\"patientId\"=:patient");
      parameters.addValue("patient", query.getPatientId());
    }
    if (query.getCurrency() != null) {
      where.append(" AND q.currency=:currency");
      parameters.addValue("currency", query.getCurrency());
    }
    if (query.getStatus() != null) {
      where.append(" AND q.status=:status");
      parameters.addValue("status", query.getStatus());
    }
    if (query.getMethod() != null) {
      where.append(" AND q.method=:method");
      parameters.addValue("method", query.getMethod());
    }
    if (!query.getSearch().isBlank()) {
      // The selected columns come exclusively from the fixed report definition.
      String textColumns =
          definition.columns().stream()
              .map(c -> "q.\"" + c.key() + "\"::text")
              .reduce((a, b) -> a + "," + b)
              .orElseThrow();
      where
          .append(" AND lower(concat_ws(' ',")
          .append(textColumns)
          .append(")) LIKE :search ESCAPE '!'");
      parameters.addValue(
          "search",
          "%"
              + query
                  .getSearch()
                  .toLowerCase(Locale.ROOT)
                  .replace("!", "!!")
                  .replace("%", "!%")
                  .replace("_", "!_")
              + "%");
    }
    return new Table(where.toString(), parameters, definition);
  }

  public long count(Table table) {
    return jdbc.queryForObject(
        "SELECT count(*) FROM (" + table.sql() + ") rows", table.parameters(), Long.class);
  }

  public List<Row> rows(Table table, String sort, String direction, int size, long offset) {
    var parameters =
        new MapSqlParameterSource(table.parameters().getValues())
            .addValue("limit", size)
            .addValue("offset", offset);
    String order = sort.equals("name") ? table.definition().defaultSort() : sort;
    if (table.definition().columns().stream().noneMatch(c -> c.key().equals(order))
        || !Set.of("asc", "desc").contains(direction))
      throw com.odontocare.shared.web.ApiException.badRequest(
          "El campo de ordenación no está permitido.");
    return jdbc.query(
        table.sql()
            + " ORDER BY \""
            + order
            + "\" "
            + direction
            + ",id ASC LIMIT :limit OFFSET :offset",
        parameters,
        (r, i) -> row(r, table.definition()));
  }

  private Row row(ResultSet r, Definition definition) throws SQLException {
    Map<String, String> values = new LinkedHashMap<>();
    for (var column : definition.columns()) {
      Object value = r.getObject(column.key());
      String text =
          value == null
              ? ""
              : value instanceof BigDecimal decimal
                  ? decimal.toPlainString()
                  : value instanceof Timestamp date
                      ? date.toLocalDateTime().toString()
                      : value.toString();
      if (column.key().equals("status")) text = definition.statuses().getOrDefault(text, text);
      if (column.key().equals("method"))
        text =
            Map.of(
                    "CASH",
                    "Efectivo",
                    "TRANSFER",
                    "Transferencia",
                    "CARD",
                    "Tarjeta",
                    "OTHER",
                    "Otro")
                .getOrDefault(text, text);
      values.put(column.key(), text);
    }
    return new Row(
        r.getString("id"),
        r.getObject("patientId", UUID.class),
        r.getObject("dentistId", UUID.class),
        r.getObject("entityId", UUID.class),
        r.getObject("documentId", UUID.class),
        r.getString("status"),
        Collections.unmodifiableMap(values));
  }

  public List<Metric> metrics(Table table, long count) {
    var result = new ArrayList<Metric>();
    result.add(new Metric("records", "Registros", "COUNT", Long.toString(count), null));
    var sums = sums(table.definition().type());
    if (sums.isEmpty()) return result;
    appendSums(table, sums.stream().filter(s -> s.kind().equals("COUNT")).toList(), false, result);
    appendSums(table, sums.stream().filter(s -> s.kind().equals("MONEY")).toList(), true, result);
    return List.copyOf(result);
  }

  private void appendSums(Table table, List<Sum> sums, boolean monetary, List<Metric> result) {
    if (sums.isEmpty()) return;
    String select =
        sums.stream()
            .map(s -> "coalesce(sum(\"" + s.column() + "\"),0) \"" + s.column() + "\"")
            .reduce((a, b) -> a + "," + b)
            .orElseThrow();
    String sql =
        "SELECT "
            + (monetary ? "currency," : "")
            + select
            + " FROM ("
            + table.sql()
            + ") summary"
            + (monetary ? " GROUP BY currency ORDER BY currency" : "");
    jdbc.query(
        sql,
        table.parameters(),
        r -> {
          for (var sum : sums)
            result.add(
                new Metric(
                    sum.column(),
                    sum.label(),
                    sum.kind(),
                    r.getBigDecimal(sum.column()).toPlainString(),
                    monetary ? r.getString("currency") : null));
        });
  }

  private List<Sum> sums(ReportType type) {
    return switch (type) {
      case APPOINTMENTS, ABSENCES -> List.of();
      case TREATMENTS ->
          List.of(
              new Sum("budget", "Presupuestado", "MONEY"),
              new Sum("charged", "Deuda neta de planes", "MONEY"),
              new Sum("completed", "Sesiones realizadas", "COUNT"),
              new Sum("periodSessions", "Sesiones del período", "COUNT"));
      case SERVICES ->
          List.of(
              new Sum("units", "Unidades individuales", "COUNT"),
              new Sum("planSessions", "Sesiones de plan", "COUNT"));
      case CHARGES ->
          List.of(
              new Sum("issued", "Cargos originales", "MONEY"),
              new Sum("adjusted", "Ajustes y anulaciones", "MONEY"),
              new Sum("amount", "Cargos netos", "MONEY"));
      case COLLECTIONS ->
          List.of(
              new Sum("received", "Pagos recibidos", "MONEY"),
              new Sum("returned", "Devoluciones y reversiones", "MONEY"),
              new Sum("amount", "Cobros netos", "MONEY"));
      case EXPENSES ->
          List.of(
              new Sum("spent", "Egresos registrados", "MONEY"),
              new Sum("recovered", "Reversiones de egresos", "MONEY"),
              new Sum("amount", "Egresos netos", "MONEY"));
      case BALANCES ->
          List.of(
              new Sum("debt", "Deuda neta", "MONEY"),
              new Sum("applied", "Pagos aplicados", "MONEY"),
              new Sum("pending", "Saldo pendiente", "MONEY"),
              new Sum("advance", "Anticipos disponibles", "MONEY"));
      case INSTALLMENTS ->
          List.of(
              new Sum("amount", "Cuotas del período", "MONEY"),
              new Sum("paid", "Pagado en cuotas", "MONEY"),
              new Sum("pending", "Cuotas pendientes", "MONEY"));
    };
  }

  public List<String> currencies() {
    return jdbc.getJdbcTemplate()
        .queryForList(
            "SELECT currency FROM treatment_plan UNION SELECT currency FROM charge_entry UNION"
                + " SELECT currency FROM money_movement ORDER BY currency",
            String.class);
  }

  public String patientName(UUID id) {
    return jdbc
        .getJdbcTemplate()
        .queryForList("SELECT full_name FROM patient WHERE id=?", String.class, id)
        .stream()
        .findFirst()
        .orElse("Paciente seleccionado");
  }

  public String dentistName(UUID id) {
    return jdbc
        .getJdbcTemplate()
        .queryForList("SELECT full_name FROM dentist WHERE id=?", String.class, id)
        .stream()
        .findFirst()
        .orElse("Profesional seleccionado");
  }
}
