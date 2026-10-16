package com.odontocare.agent.repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class AgentPriceSequenceRepository {
  public record Sequence(Set<UUID> services, Instant lastQueryAt, int warnings, UUID deniedRun) {}

  private final JdbcTemplate jdbc;

  public AgentPriceSequenceRepository(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public int limit() {
    return jdbc.queryForObject(
        "SELECT max_price_services FROM agent_policy WHERE id=1", Integer.class);
  }

  public Sequence lock(UUID conversation, String source) {
    jdbc.update(
        "INSERT INTO agent_price_sequence(conversation_id,source) VALUES(?,?) ON CONFLICT DO"
            + " NOTHING",
        conversation,
        source);
    return jdbc.queryForObject(
        "SELECT * FROM agent_price_sequence WHERE conversation_id=? AND source=? FOR UPDATE",
        (r, i) -> {
          var values = (UUID[]) r.getArray("service_ids").getArray();
          var time = r.getTimestamp("last_query_at");
          return new Sequence(
              new LinkedHashSet<>(Arrays.asList(values)),
              time == null ? null : time.toInstant(),
              r.getInt("warning_count"),
              r.getObject("last_denied_run_id", UUID.class));
        },
        conversation,
        source);
  }

  public void save(UUID conversation, String source, Sequence sequence) {
    jdbc.update(
        "UPDATE agent_price_sequence SET"
            + " service_ids=?::uuid[],last_query_at=?,warning_count=?,last_denied_run_id=? WHERE"
            + " conversation_id=? AND source=?",
        "{" + String.join(",", sequence.services().stream().map(UUID::toString).toList()) + "}",
        sequence.lastQueryAt() == null ? null : Timestamp.from(sequence.lastQueryAt()),
        sequence.warnings(),
        sequence.deniedRun(),
        conversation,
        source);
  }

  public List<String> lookups(UUID run) {
    return jdbc.queryForList(
        "SELECT result_json::text FROM agent_step WHERE run_id=? AND name='consultar_servicios' AND"
            + " state='OK' ORDER BY ordinal",
        String.class,
        run);
  }
}
