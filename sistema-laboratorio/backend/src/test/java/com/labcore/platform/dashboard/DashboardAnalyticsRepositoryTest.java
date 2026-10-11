package com.labcore.platform.dashboard;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;

import com.labcore.platform.dashboard.DashboardAnalyticsRepository.OperationalCounts;
import java.sql.Types;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

@ExtendWith(MockitoExtension.class)
class DashboardAnalyticsRepositoryTest {
  @Mock private NamedParameterJdbcTemplate jdbc;

  @Test
  @SuppressWarnings("unchecked")
  void bindsDashboardInstantsAsTimestampWithTimeZone() {
    ArgumentCaptor<MapSqlParameterSource> parameters =
        ArgumentCaptor.forClass(MapSqlParameterSource.class);
    doReturn(new OperationalCounts(1, 2))
        .when(jdbc)
        .queryForObject(anyString(), parameters.capture(), any(RowMapper.class));
    DashboardAnalyticsRepository repository = new DashboardAnalyticsRepository(jdbc);
    DashboardScope scope =
        new DashboardScope(
            LocalDate.of(2026, 9, 1),
            LocalDate.of(2026, 9, 15),
            Instant.parse("2026-09-01T05:00:00Z"),
            Instant.parse("2026-09-16T05:00:00Z"),
            null,
            null,
            "America/Lima");

    repository.operationalCounts(scope);

    MapSqlParameterSource captured = parameters.getValue();
    assertThat(captured.getValue("fromInclusive")).isInstanceOf(OffsetDateTime.class);
    assertThat(captured.getValue("toExclusive")).isInstanceOf(OffsetDateTime.class);
    assertThat(captured.getSqlType("fromInclusive")).isEqualTo(Types.TIMESTAMP_WITH_TIMEZONE);
    assertThat(captured.getSqlType("toExclusive")).isEqualTo(Types.TIMESTAMP_WITH_TIMEZONE);
  }
}
