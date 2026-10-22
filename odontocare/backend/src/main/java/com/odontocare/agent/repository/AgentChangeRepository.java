package com.odontocare.agent.repository;

import com.odontocare.agent.dto.SupervisionContracts.Change;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;
import org.springframework.jdbc.core.*;
import org.springframework.stereotype.Repository;

@Repository
public class AgentChangeRepository {
  private final JdbcTemplate jdbc;

  public AgentChangeRepository(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  private static final RowMapper<Change> MAPPER =
      (r, i) ->
          new Change(
              r.getObject("id", UUID.class),
              r.getObject("run_id", UUID.class),
              r.getObject("conversation_id", UUID.class),
              r.getString("source"),
              r.getString("action"),
              r.getObject("appointment_id", UUID.class),
              r.getObject("patient_id", UUID.class),
              r.getLong("appointment_version"),
              r.getObject("slot_id", UUID.class),
              r.getString("reason"),
              r.getString("summary"),
              r.getString("confirmation_code"),
              r.getString("state"),
              r.getTimestamp("created_at").toInstant(),
              r.getTimestamp("expires_at").toInstant());

  public Optional<Change> current(UUID conversation, String source) {
    return jdbc
        .query(
            "SELECT c.* FROM agent_change_proposal c JOIN agent_run r ON r.id=c.run_id WHERE"
                + " c.conversation_id=? AND c.source=? ORDER BY r.sequence_no DESC LIMIT 1",
            MAPPER,
            conversation,
            source)
        .stream()
        .findFirst();
  }

  public Optional<Change> byRun(UUID run) {
    return jdbc.query("SELECT * FROM agent_change_proposal WHERE run_id=?", MAPPER, run).stream()
        .findFirst();
  }

  public Optional<Change> byCode(UUID conversation, String code) {
    return jdbc
        .query(
            "SELECT * FROM agent_change_proposal WHERE conversation_id=? AND confirmation_code=?"
                + " FOR UPDATE",
            MAPPER,
            conversation,
            code)
        .stream()
        .findFirst();
  }

  public boolean existsCode(UUID conversation, String code) {
    return Boolean.TRUE.equals(
        jdbc.queryForObject(
            "SELECT EXISTS(SELECT 1 FROM agent_change_proposal WHERE conversation_id=? AND"
                + " confirmation_code=?)",
            Boolean.class,
            conversation,
            code));
  }

  public void discard(UUID conversation, String source) {
    jdbc.update(
        "UPDATE agent_change_proposal SET state='SUPERSEDED' WHERE conversation_id=? AND source=?"
            + " AND state='PENDING'",
        conversation,
        source);
    jdbc.update(
        "UPDATE agent_proposal SET state='SUPERSEDED' WHERE conversation_id=? AND source=? AND"
            + " state='PENDING'",
        conversation,
        source);
  }

  public Change propose(
      UUID run,
      UUID conversation,
      String source,
      String action,
      UUID appointment,
      UUID patient,
      long version,
      UUID slot,
      String reason,
      String summary,
      Instant now) {
    var prior = byRun(run);
    if (prior.isPresent()) return prior.get();
    discard(conversation, source);
    UUID id = UUID.randomUUID();
    String code = id.toString().replace("-", "").substring(0, 8).toUpperCase(Locale.ROOT);
    jdbc.update(
        "INSERT INTO"
            + " agent_change_proposal(id,run_id,conversation_id,source,action,appointment_id,patient_id,appointment_version,slot_id,reason,summary,confirmation_code,created_at,expires_at)"
            + " VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
        id,
        run,
        conversation,
        source,
        action,
        appointment,
        patient,
        version,
        slot,
        reason,
        summary,
        code,
        Timestamp.from(now),
        Timestamp.from(now.plusSeconds(1800)));
    return byRun(run).orElseThrow();
  }

  public void state(UUID id, String state) {
    jdbc.update("UPDATE agent_change_proposal SET state=? WHERE id=?", state, id);
  }

  public void confirm(UUID id, UUID message) {
    jdbc.update(
        "UPDATE agent_change_proposal SET state='CONFIRMED',confirmation_message_id=? WHERE id=?",
        message,
        id);
  }

  public UUID reference(
      UUID conversation, String source, UUID patient, UUID appointment, Instant now) {
    UUID id = UUID.randomUUID();
    jdbc.update(
        "INSERT INTO"
            + " agent_appointment_reference(id,conversation_id,source,patient_id,appointment_id,expires_at)"
            + " VALUES(?,?,?,?,?,?)",
        id,
        conversation,
        source,
        patient,
        appointment,
        Timestamp.from(now.plusSeconds(1800)));
    return id;
  }

  public Optional<Map<String, Object>> reference(
      UUID id, UUID conversation, String source, Instant now) {
    return jdbc
        .queryForList(
            "SELECT * FROM agent_appointment_reference WHERE id=? AND conversation_id=? AND"
                + " source=? AND expires_at>?",
            id,
            conversation,
            source,
            Timestamp.from(now))
        .stream()
        .findFirst();
  }

  public List<Map<String, Object>> appointments(UUID patient, String search, int page) {
    String pattern =
        "%"
            + search
                .toLowerCase(Locale.ROOT)
                .replace("!", "!!")
                .replace("%", "!%")
                .replace("_", "!_")
            + "%";
    return jdbc.queryForList(
        "SELECT"
            + " id,patient_id,starts_at,ends_at,duration_minutes,service_id,service_name,dentist_id,dentist_name,status,version"
            + " FROM appointment WHERE patient_id=? AND starts_at>=now() AND status IN"
            + " ('RESERVED','CONFIRMED') AND (lower(service_name) LIKE ? ESCAPE '!' OR"
            + " lower(dentist_name) LIKE ? ESCAPE '!') ORDER BY starts_at,id LIMIT 5 OFFSET ?",
        patient,
        pattern,
        pattern,
        page * 5);
  }

  public Map<String, Object> appointment(UUID id, boolean lock) {
    return jdbc.queryForMap(
        "SELECT"
            + " id,patient_id,starts_at,duration_minutes,service_id,service_name,dentist_id,dentist_name,status,version"
            + " FROM appointment WHERE id=?"
            + (lock ? " FOR UPDATE" : ""),
        id);
  }
}
