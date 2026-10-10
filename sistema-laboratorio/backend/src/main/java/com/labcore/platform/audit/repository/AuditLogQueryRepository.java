package com.labcore.platform.audit;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class AuditLogQueryRepository {
  private static final String EVENTS_QUERY =
      """
      SELECT soh.id, 'SERVICE_ORDER' AS module, soh.service_order_id AS resource_id,
             service_order.code AS resource_code, soh.action AS action,
             COALESCE(soh.change_note, 'Sin detalle adicional') AS description,
             soh.performed_by AS actor, soh.occurred_at
      FROM service_order_history soh
      JOIN service_order ON service_order.id = soh.service_order_id
      UNION ALL
      SELECT woh.id, 'WORK_ORDER', woh.work_order_id, work_order.code, woh.action,
             COALESCE(woh.change_note, 'Sin detalle adicional'), woh.performed_by, woh.occurred_at
      FROM work_order_history woh
      JOIN work_order ON work_order.id = woh.work_order_id
      UNION ALL
      SELECT rcl.id, 'RESULT', result_entry.id, work_order.code, 'RESULT_CORRECTED',
             CONCAT('Corrección de ', result_value.label, ': ',
                    COALESCE(rcl.old_value_text, rcl.old_value_number::text, '—'), ' → ',
                    COALESCE(rcl.new_value_text, rcl.new_value_number::text, '—'),
                    '. Motivo: ', rcl.reason),
             COALESCE(app_user.email, rcl.changed_by::text), rcl.changed_at
      FROM result_change_log rcl
      JOIN result_value ON result_value.id = rcl.result_value_id
      JOIN result_entry ON result_entry.id = result_value.result_entry_id
      JOIN work_order_test ON work_order_test.id = result_entry.work_order_test_id
      JOIN work_order_sample ON work_order_sample.id = work_order_test.work_order_sample_id
      JOIN work_order ON work_order.id = work_order_sample.work_order_id
      LEFT JOIN app_user ON app_user.id = rcl.changed_by
      UNION ALL
      SELECT rmcl.id, 'RESULT_MATRIX', result_matrix.id, work_order.code, 'MATRIX_CORRECTED',
             CONCAT('Corrección matricial en ', rmcl.field_name, ': ',
                    COALESCE(rmcl.old_value, '—'), ' → ', COALESCE(rmcl.new_value, '—'),
                    '. Motivo: ', rmcl.reason),
             COALESCE(app_user.email, rmcl.changed_by::text), rmcl.changed_at
      FROM result_matrix_change_log rmcl
      JOIN result_matrix ON result_matrix.id = rmcl.result_matrix_id
      JOIN result_entry ON result_entry.id = result_matrix.result_entry_id
      JOIN work_order_test ON work_order_test.id = result_entry.work_order_test_id
      JOIN work_order_sample ON work_order_sample.id = work_order_test.work_order_sample_id
      JOIN work_order ON work_order.id = work_order_sample.work_order_id
      LEFT JOIN app_user ON app_user.id = rmcl.changed_by
      UNION ALL
      SELECT rrh.id, 'RESULT_REVIEW', result_entry.id, work_order.code, rrh.action,
             CONCAT('Revisión de resultado, versión ', rrh.result_revision,
                    ': ', COALESCE(rrh.previous_status, 'PENDING'), ' → ', rrh.new_status,
                    CASE WHEN rrh.reason IS NULL THEN '' ELSE CONCAT('. Motivo: ', rrh.reason) END,
                    CASE WHEN rrh.comment IS NULL THEN '' ELSE CONCAT('. Comentario: ', rrh.comment) END,
                    '. Rol: ', rrh.performed_by_role),
             COALESCE(app_user.email, rrh.performed_by::text), rrh.performed_at
      FROM result_review_history rrh
      JOIN result_entry ON result_entry.id = rrh.result_entry_id
      JOIN work_order_test ON work_order_test.id = result_entry.work_order_test_id
      JOIN work_order_sample ON work_order_sample.id = work_order_test.work_order_sample_id
      JOIN work_order ON work_order.id = work_order_sample.work_order_id
      LEFT JOIN app_user ON app_user.id = rrh.performed_by
      UNION ALL
      SELECT rcs.id, 'RESULT_CALCULATION', result_entry.id, work_order.code,
             'RESULT_RECALCULATED',
             CONCAT('Cálculo ', rcs.field_key, ' (versión ', rcs.result_revision,
                    '): ', rcs.formula_used, ' = ', rcs.calculated_value::text,
                    '. Entradas: ', rcs.calculation_inputs, '. Rol: ', rcs.calculated_by_role),
             COALESCE(app_user.email, rcs.calculated_by::text), rcs.calculated_at
      FROM result_calculation_snapshot rcs
      JOIN result_entry ON result_entry.id = rcs.result_entry_id
      JOIN work_order_test ON work_order_test.id = result_entry.work_order_test_id
      JOIN work_order_sample ON work_order_sample.id = work_order_test.work_order_sample_id
      JOIN work_order ON work_order.id = work_order_sample.work_order_id
      LEFT JOIN app_user ON app_user.id = rcs.calculated_by
      UNION ALL
      SELECT lrh.id, 'REPORT', lrh.laboratory_report_id, laboratory_report.code, lrh.action,
             COALESCE(lrh.change_note, 'Sin detalle adicional'), lrh.performed_by, lrh.occurred_at
      FROM laboratory_report_history lrh
      JOIN laboratory_report ON laboratory_report.id = lrh.laboratory_report_id
      UNION ALL
      SELECT ial.id, 'IDENTITY_ACCESS', ial.resource_id, ial.resource_code, ial.action,
             ial.reason, ial.actor, ial.accessed_at
      FROM identity_access_log ial
      UNION ALL
      SELECT oph.id, 'ORGANIZATION', oph.organization_id, organization_profile.commercial_name,
             oph.action, oph.change_note, oph.performed_by, oph.occurred_at
      FROM organization_profile_history oph
      JOIN organization_profile ON organization_profile.id = oph.organization_id
      """;

  private final NamedParameterJdbcTemplate jdbcTemplate;

  public AuditLogQueryRepository(NamedParameterJdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  public List<AuditLogDtos.Entry> find(
      String module, String actor, Instant from, Instant to, int offset, int limit) {
    QueryParts query = filteredQuery(module, actor, from, to);
    query.parameters().put("offset", offset);
    query.parameters().put("limit", limit);
    return jdbcTemplate.query(
        "SELECT * FROM ("
            + EVENTS_QUERY
            + ") event "
            + query.whereClause()
            + " ORDER BY event.occurred_at DESC OFFSET :offset LIMIT :limit",
        query.parameters(),
        this::map);
  }

  public long count(String module, String actor, Instant from, Instant to) {
    QueryParts query = filteredQuery(module, actor, from, to);
    Long count =
        jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM (" + EVENTS_QUERY + ") event " + query.whereClause(),
            query.parameters(),
            Long.class);
    return count == null ? 0 : count;
  }

  private QueryParts filteredQuery(String module, String actor, Instant from, Instant to) {
    List<String> filters = new java.util.ArrayList<>();
    Map<String, Object> parameters = new HashMap<>();
    if (module != null && !module.isBlank()) {
      filters.add("event.module = :module");
      parameters.put("module", module.trim().toUpperCase());
    }
    if (actor != null && !actor.isBlank()) {
      filters.add("LOWER(event.actor) LIKE :actor");
      parameters.put("actor", "%" + actor.trim().toLowerCase(java.util.Locale.ROOT) + "%");
    }
    if (from != null) {
      filters.add("event.occurred_at >= :from");
      parameters.put("from", Timestamp.from(from));
    }
    if (to != null) {
      filters.add("event.occurred_at <= :to");
      parameters.put("to", Timestamp.from(to));
    }
    return new QueryParts(
        filters.isEmpty() ? "" : "WHERE " + String.join(" AND ", filters), parameters);
  }

  private AuditLogDtos.Entry map(ResultSet resultSet, int rowNumber) throws SQLException {
    return new AuditLogDtos.Entry(
        resultSet.getObject("id", UUID.class),
        resultSet.getString("module"),
        resultSet.getObject("resource_id", UUID.class),
        resultSet.getString("resource_code"),
        resultSet.getString("action"),
        resultSet.getString("description"),
        resultSet.getString("actor"),
        resultSet.getTimestamp("occurred_at").toInstant());
  }

  private record QueryParts(String whereClause, Map<String, Object> parameters) {}
}
