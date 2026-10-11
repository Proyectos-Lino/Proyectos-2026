package com.labcore.platform.dashboard;

import java.sql.Types;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class DashboardAnalyticsRepository {
  private static final String ORDER_SCOPE =
      """
      so.created_at >= :fromInclusive
      AND so.created_at < :toExclusive
      AND (CAST(:areaId AS uuid) IS NULL OR so.laboratory_area_id = :areaId)
      AND (
          CAST(:analystId AS uuid) IS NULL
          OR EXISTS (
              SELECT 1
              FROM work_order analyst_wo
              JOIN work_order_sample analyst_wos
                ON analyst_wos.work_order_id = analyst_wo.id
              JOIN work_order_test analyst_wot
                ON analyst_wot.work_order_sample_id = analyst_wos.id
              WHERE analyst_wo.service_order_id = so.id
                AND analyst_wot.assigned_analyst_id = :analystId
          )
      )
      """;

  private final NamedParameterJdbcTemplate jdbc;

  public DashboardAnalyticsRepository(NamedParameterJdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public OperationalCounts operationalCounts(DashboardScope scope) {
    String sql =
        """
        SELECT COUNT(DISTINCT so.id) AS orders,
               COUNT(DISTINCT sos.id) AS samples
        FROM service_order so
        LEFT JOIN service_order_sample sos ON sos.service_order_id = so.id
        WHERE
        """
            + ORDER_SCOPE;
    return jdbc.queryForObject(
        sql,
        parameters(scope),
        (result, row) ->
            new OperationalCounts(result.getLong("orders"), result.getLong("samples")));
  }

  public long activeServices(UUID areaId) {
    return value(
        """
        SELECT COUNT(*)
        FROM laboratory_service
        WHERE active = TRUE
          AND (CAST(:areaId AS uuid) IS NULL OR laboratory_area_id = :areaId)
        """,
        new MapSqlParameterSource("areaId", areaId));
  }

  public long pendingResults(DashboardScope scope) {
    return value(
        """
        SELECT COUNT(*)
        FROM work_order_test wot
        JOIN work_order_sample wos ON wos.id = wot.work_order_sample_id
        JOIN work_order wo ON wo.id = wos.work_order_id
        WHERE wot.status IN ('PENDING', 'RECORDED', 'CORRECTION_REQUESTED', 'CORRECTED')
          AND wo.status NOT IN ('CANCELLED', 'CLOSED')
          AND (CAST(:areaId AS uuid) IS NULL OR wo.laboratory_area_id = :areaId)
          AND (CAST(:analystId AS uuid) IS NULL OR wot.assigned_analyst_id = :analystId)
        """,
        parameters(scope));
  }

  public long validationQueue(DashboardScope scope) {
    return value(
        """
        SELECT COUNT(*)
        FROM result_entry re
        JOIN work_order_test wot ON wot.id = re.work_order_test_id
        JOIN work_order_sample wos ON wos.id = wot.work_order_sample_id
        JOIN work_order wo ON wo.id = wos.work_order_id
        WHERE re.status IN ('RECORDED', 'CORRECTED')
          AND (CAST(:areaId AS uuid) IS NULL OR wo.laboratory_area_id = :areaId)
        """,
        parameters(scope));
  }

  public long issuedReports(DashboardScope scope) {
    return value(
        """
        SELECT COUNT(*)
        FROM laboratory_report lr
        JOIN service_order so ON so.id = lr.service_order_id
        WHERE lr.status = 'ISSUED'
          AND lr.issued_at >= :fromInclusive
          AND lr.issued_at < :toExclusive
          AND (CAST(:areaId AS uuid) IS NULL OR so.laboratory_area_id = :areaId)
        """,
        parameters(scope));
  }

  public List<DashboardDtos.TimeSeriesPoint> receivedSamplesTrend(DashboardScope scope) {
    String sql =
        """
        SELECT (sos.created_at AT TIME ZONE :timeZone)::date AS activity_date,
               COUNT(*) AS total
        FROM service_order_sample sos
        JOIN service_order so ON so.id = sos.service_order_id
        WHERE sos.created_at >= :fromInclusive
          AND sos.created_at < :toExclusive
          AND (CAST(:areaId AS uuid) IS NULL OR so.laboratory_area_id = :areaId)
        GROUP BY activity_date
        ORDER BY activity_date
        """;
    return jdbc.query(
        sql,
        parameters(scope),
        (result, row) ->
            new DashboardDtos.TimeSeriesPoint(
                result.getObject("activity_date", LocalDate.class), result.getLong("total")));
  }

  public List<DashboardDtos.TimeSeriesPoint> analystResultTrend(DashboardScope scope) {
    String sql =
        """
        SELECT (re.recorded_at AT TIME ZONE :timeZone)::date AS activity_date,
               COUNT(*) AS total
        FROM result_entry re
        JOIN work_order_test wot ON wot.id = re.work_order_test_id
        JOIN work_order_sample wos ON wos.id = wot.work_order_sample_id
        JOIN work_order wo ON wo.id = wos.work_order_id
        JOIN service_order so ON so.id = wo.service_order_id
        WHERE re.recorded_by = :analystId
          AND re.recorded_at >= :fromInclusive
          AND re.recorded_at < :toExclusive
          AND wot.assigned_analyst_id = :analystId
          AND (CAST(:areaId AS uuid) IS NULL OR so.laboratory_area_id = :areaId)
        GROUP BY activity_date
        ORDER BY activity_date
        """;
    return jdbc.query(
        sql,
        parameters(scope),
        (result, row) ->
            new DashboardDtos.TimeSeriesPoint(
                result.getObject("activity_date", LocalDate.class), result.getLong("total")));
  }

  public List<DashboardDtos.RankingItem> topServices(DashboardScope scope) {
    String sql =
        """
        SELECT service.id,
               service.name,
               area.name AS area_name,
               COUNT(DISTINCT (sos.id, service.id)) AS total
        FROM service_order so
        JOIN service_order_sample sos ON sos.service_order_id = so.id
        JOIN service_order_sample_test sost ON sost.service_order_sample_id = sos.id
        JOIN laboratory_service service ON service.id = sost.laboratory_service_id
        JOIN laboratory_area area ON area.id = service.laboratory_area_id
        WHERE
        """
            + ORDER_SCOPE
            + """
          AND (
              CAST(:analystId AS uuid) IS NULL
              OR EXISTS (
                  SELECT 1
                  FROM work_order_test analyst_wot
                  WHERE analyst_wot.source_sample_test_id = sost.id
                    AND analyst_wot.assigned_analyst_id = :analystId
              )
          )
        GROUP BY service.id, service.name, area.name
        ORDER BY total DESC, service.name
        LIMIT 6
        """;
    return jdbc.query(
        sql,
        parameters(scope),
        (result, row) ->
            new DashboardDtos.RankingItem(
                result.getObject("id", UUID.class),
                result.getString("name"),
                result.getLong("total"),
                result.getString("area_name"),
                "/catalogs?tab=services"));
  }

  public List<DashboardDtos.DistributionItem> distribution(
      DashboardScope scope, DistributionDimension dimension) {
    String query =
        switch (dimension) {
          case LABORATORY ->
              """
              SELECT area.id::text AS item_key, area.name AS label,
                     COUNT(DISTINCT sos.id) AS total
              FROM service_order so
              JOIN laboratory_area area ON area.id = so.laboratory_area_id
              JOIN service_order_sample sos ON sos.service_order_id = so.id
              WHERE %s
                AND (
                    CAST(:analystId AS uuid) IS NULL
                    OR EXISTS (
                        SELECT 1
                        FROM service_order_sample_test analyst_sost
                        JOIN work_order_test analyst_wot
                          ON analyst_wot.source_sample_test_id = analyst_sost.id
                        WHERE analyst_sost.service_order_sample_id = sos.id
                          AND analyst_wot.assigned_analyst_id = :analystId
                    )
                )
              GROUP BY area.id, area.name
              ORDER BY total DESC, area.name
              """
                  .formatted(ORDER_SCOPE);
          case SAMPLE_TYPE ->
              """
              SELECT type.id::text AS item_key, type.name AS label, COUNT(*) AS total
              FROM service_order so
              JOIN service_order_sample sos ON sos.service_order_id = so.id
              JOIN sample_type type ON type.id = sos.sample_type_id
              WHERE %s
                AND (
                    CAST(:analystId AS uuid) IS NULL
                    OR EXISTS (
                        SELECT 1
                        FROM service_order_sample_test analyst_sost
                        JOIN work_order_test analyst_wot
                          ON analyst_wot.source_sample_test_id = analyst_sost.id
                        WHERE analyst_sost.service_order_sample_id = sos.id
                          AND analyst_wot.assigned_analyst_id = :analystId
                    )
                )
              GROUP BY type.id, type.name
              ORDER BY total DESC, type.name
              """
                  .formatted(ORDER_SCOPE);
          case SERVICE ->
              """
              SELECT service.id::text AS item_key, service.name AS label,
                     COUNT(DISTINCT (sos.id, service.id)) AS total
              FROM service_order so
              JOIN service_order_sample sos ON sos.service_order_id = so.id
              JOIN service_order_sample_test sost ON sost.service_order_sample_id = sos.id
              JOIN laboratory_service service ON service.id = sost.laboratory_service_id
              WHERE %s
                AND (
                    CAST(:analystId AS uuid) IS NULL
                    OR EXISTS (
                        SELECT 1 FROM work_order_test analyst_wot
                        WHERE analyst_wot.source_sample_test_id = sost.id
                          AND analyst_wot.assigned_analyst_id = :analystId
                    )
                )
              GROUP BY service.id, service.name
              ORDER BY total DESC, service.name
              LIMIT 8
              """
                  .formatted(ORDER_SCOPE);
          case SERVICE_ORDER_STATUS ->
              """
              SELECT so.status AS item_key, so.status AS label, COUNT(*) AS total
              FROM service_order so
              WHERE %s
              GROUP BY so.status
              ORDER BY total DESC, so.status
              """
                  .formatted(ORDER_SCOPE);
          case WORK_ORDER_STATUS ->
              """
              SELECT wo.status AS item_key, wo.status AS label, COUNT(DISTINCT wo.id) AS total
              FROM service_order so
              JOIN work_order wo ON wo.service_order_id = so.id
              WHERE %s
                AND (
                    CAST(:analystId AS uuid) IS NULL
                    OR EXISTS (
                        SELECT 1
                        FROM work_order_sample analyst_wos
                        JOIN work_order_test analyst_wot
                          ON analyst_wot.work_order_sample_id = analyst_wos.id
                        WHERE analyst_wos.work_order_id = wo.id
                          AND analyst_wot.assigned_analyst_id = :analystId
                    )
                )
              GROUP BY wo.status
              ORDER BY total DESC, wo.status
              """
                  .formatted(ORDER_SCOPE);
        };
    return jdbc.query(
        query,
        parameters(scope),
        (result, row) ->
            new DashboardDtos.DistributionItem(
                result.getString("item_key"), result.getString("label"), result.getLong("total")));
  }

  public List<DashboardDtos.CycleTime> cycleTimes(DashboardScope scope) {
    MapSqlParameterSource parameters = parameters(scope);
    return List.of(
        cycle(
            "receptionToWorkOrder",
            "Recepción a orden técnica",
            """
            SELECT AVG(EXTRACT(EPOCH FROM (wo.created_at - COALESCE(so.confirmed_at, so.created_at))) / 3600.0)
            FROM work_order wo
            JOIN service_order so ON so.id = wo.service_order_id
            WHERE %s
            """
                .formatted(ORDER_SCOPE),
            "Tiempo medio hasta asignar el trabajo",
            parameters),
        cycle(
            "workOrderToEntry",
            "Orden técnica a registro",
            """
            SELECT AVG(EXTRACT(EPOCH FROM (re.recorded_at - wo.created_at)) / 3600.0)
            FROM result_entry re
            JOIN work_order_test wot ON wot.id = re.work_order_test_id
            JOIN work_order_sample wos ON wos.id = wot.work_order_sample_id
            JOIN work_order wo ON wo.id = wos.work_order_id
            JOIN service_order so ON so.id = wo.service_order_id
            WHERE %s
              AND (CAST(:analystId AS uuid) IS NULL OR wot.assigned_analyst_id = :analystId)
            """
                .formatted(ORDER_SCOPE),
            "Tiempo medio de procesamiento analítico",
            parameters),
        cycle(
            "entryToValidation",
            "Registro a validación",
            """
            SELECT AVG(EXTRACT(EPOCH FROM (rv.validated_at - re.recorded_at)) / 3600.0)
            FROM result_validation rv
            JOIN result_entry re ON re.id = rv.result_entry_id
            JOIN work_order_test wot ON wot.id = re.work_order_test_id
            JOIN work_order_sample wos ON wos.id = wot.work_order_sample_id
            JOIN work_order wo ON wo.id = wos.work_order_id
            JOIN service_order so ON so.id = wo.service_order_id
            WHERE %s
              AND (CAST(:analystId AS uuid) IS NULL OR wot.assigned_analyst_id = :analystId)
            """
                .formatted(ORDER_SCOPE),
            "Tiempo medio de revisión técnica",
            parameters),
        cycle(
            "validationToReport",
            "Validación a informe",
            """
            WITH report_validation AS (
                SELECT lr.id, lr.issued_at, MAX(rv.validated_at) AS validated_at
                FROM laboratory_report lr
                JOIN service_order so ON so.id = lr.service_order_id
                JOIN work_order wo ON wo.service_order_id = so.id
                JOIN work_order_sample wos ON wos.work_order_id = wo.id
                JOIN work_order_test wot ON wot.work_order_sample_id = wos.id
                JOIN result_entry re ON re.work_order_test_id = wot.id
                JOIN result_validation rv ON rv.result_entry_id = re.id
                WHERE lr.status = 'ISSUED' AND %s
                GROUP BY lr.id, lr.issued_at
            )
            SELECT AVG(EXTRACT(EPOCH FROM (issued_at - validated_at)) / 3600.0)
            FROM report_validation
            """
                .formatted(ORDER_SCOPE),
            "Tiempo medio para emitir el documento",
            parameters));
  }

  private DashboardDtos.CycleTime cycle(
      String key, String label, String sql, String context, MapSqlParameterSource parameters) {
    Double hours = jdbc.queryForObject(sql, parameters, Double.class);
    return new DashboardDtos.CycleTime(key, label, hours, context);
  }

  private long value(String sql, MapSqlParameterSource parameters) {
    Long result = jdbc.queryForObject(sql, parameters, Long.class);
    return result == null ? 0L : result;
  }

  private MapSqlParameterSource parameters(DashboardScope scope) {
    return new MapSqlParameterSource()
        .addValue(
            "fromInclusive",
            scope.fromInclusive().atOffset(ZoneOffset.UTC),
            Types.TIMESTAMP_WITH_TIMEZONE)
        .addValue(
            "toExclusive",
            scope.toExclusive().atOffset(ZoneOffset.UTC),
            Types.TIMESTAMP_WITH_TIMEZONE)
        .addValue("areaId", scope.laboratoryAreaId())
        .addValue("analystId", scope.analystId())
        .addValue("timeZone", scope.timeZone());
  }

  public record OperationalCounts(long serviceOrders, long samples) {}

  public enum DistributionDimension {
    LABORATORY,
    SAMPLE_TYPE,
    SERVICE,
    SERVICE_ORDER_STATUS,
    WORK_ORDER_STATUS
  }
}
