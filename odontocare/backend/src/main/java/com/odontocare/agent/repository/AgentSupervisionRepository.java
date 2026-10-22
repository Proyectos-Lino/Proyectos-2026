package com.odontocare.agent.repository;

import com.odontocare.agent.dto.SupervisionContracts.*;
import java.sql.Timestamp;
import java.time.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.ObjectMapper;

@Repository
public class AgentSupervisionRepository {
  private final JdbcTemplate jdbc;
  private final ObjectMapper mapper;

  public AgentSupervisionRepository(JdbcTemplate jdbc, ObjectMapper mapper) {
    this.jdbc = jdbc;
    this.mapper = mapper;
  }

  public Policy policy(boolean lock) {
    return jdbc.query(
            "SELECT * FROM agent_policy WHERE id=1" + (lock ? " FOR UPDATE" : ""),
            (r, i) ->
                new Policy(
                    r.getLong("version"),
                    r.getBoolean("enabled"),
                    Arrays.asList(
                        mapper.readValue(
                            r.getString("schedule"),
                            com.odontocare.agent.dto.SupervisionContracts.Period[].class)),
                    r.getInt("change_lead_minutes"),
                    r.getBoolean("allow_reschedule"),
                    r.getBoolean("allow_cancel"),
                    r.getString("handoff_text"),
                    r.getString("clinical_text"),
                    r.getString("closed_text"),
                    r.getString("failure_text")))
        .getFirst();
  }

  public void savePolicy(Policy p) {
    jdbc.update(
        "UPDATE agent_policy SET"
            + " version=version+1,enabled=?,schedule=?::jsonb,change_lead_minutes=?,allow_reschedule=?,allow_cancel=?,handoff_text=?,clinical_text=?,closed_text=?,failure_text=?"
            + " WHERE id=1",
        p.enabled(),
        mapper.writeValueAsString(p.schedule()),
        p.changeLeadMinutes(),
        p.allowReschedule(),
        p.allowCancel(),
        p.handoffText().strip(),
        p.clinicalText().strip(),
        p.closedText().strip(),
        p.failureText().strip());
  }

  public void initialize(UUID conversation, String source) {
    jdbc.update(
        "INSERT INTO agent_supervision(conversation_id) VALUES(?) ON CONFLICT DO NOTHING",
        conversation);
    jdbc.update(
        "INSERT INTO agent_request_context(conversation_id,source) VALUES(?,?) ON CONFLICT DO"
            + " NOTHING",
        conversation,
        source);
  }

  public Map<String, Object> control(UUID conversation, boolean lock) {
    return jdbc.queryForMap(
        "SELECT * FROM agent_supervision WHERE conversation_id=?" + (lock ? " FOR UPDATE" : ""),
        conversation);
  }

  public void control(UUID conversation, String mode, UUID actor, String reason, Instant now) {
    jdbc.update(
        "UPDATE agent_supervision SET"
            + " mode=?,generation=generation+1,assigned_user_id=?,reason=?,updated_at=? WHERE"
            + " conversation_id=?",
        mode,
        actor,
        reason,
        Timestamp.from(now),
        conversation);
  }

  public void pause(UUID conversation, Instant now) {
    jdbc.update(
        "UPDATE agent_run SET state='PAUSED',operational_result='HUMAN_CONTROL',updated_at=? WHERE"
            + " conversation_id=? AND state IN ('QUEUED','PROCESSING')",
        Timestamp.from(now),
        conversation);
    jdbc.update(
        "UPDATE kapso_message SET"
            + " status='FAILED',error_code='HUMAN_CONTROL',error_message='Recepción asumió el"
            + " control; respuesta automática suprimida.',updated_at=? WHERE conversation_id=? AND"
            + " source='AGENT' AND status='QUEUED' AND coalesce(error_code,'')<>'HANDOFF_NOTICE'",
        Timestamp.from(now),
        conversation);
    jdbc.update(
        "UPDATE agent_request_context SET state='REFERRED',updated_at=? WHERE conversation_id=?",
        Timestamp.from(now),
        conversation);
    jdbc.update(
        "UPDATE agent_proposal SET state='SUPERSEDED' WHERE conversation_id=? AND state='PENDING'",
        conversation);
    jdbc.update(
        "UPDATE agent_change_proposal SET state='SUPERSEDED' WHERE conversation_id=? AND"
            + " state='PENDING'",
        conversation);
  }

  public void release(UUID conversation, Instant now) {
    jdbc.update(
        "UPDATE agent_run SET state='GROUPED',updated_at=? WHERE conversation_id=? AND"
            + " state='PAUSED'",
        Timestamp.from(now),
        conversation);
    jdbc.update(
        "UPDATE agent_request_context SET state='INFORMATION_PENDING',verified_at=NULL,updated_at=?"
            + " WHERE conversation_id=?",
        Timestamp.from(now),
        conversation);
  }

  public void attachRun(UUID run, UUID conversation, String provider) {
    jdbc.update(
        "UPDATE agent_run SET control_generation=(SELECT generation FROM agent_supervision WHERE"
            + " conversation_id=?),flow_version='supervised-v7.10',provider=? WHERE id=?",
        conversation,
        provider,
        run);
  }

  public boolean matches(UUID run, UUID conversation) {
    return Boolean.TRUE.equals(
        jdbc.queryForObject(
            "SELECT EXISTS(SELECT 1 FROM agent_run r JOIN agent_supervision s ON"
                + " s.conversation_id=r.conversation_id WHERE r.id=? AND s.conversation_id=? AND"
                + " s.mode='AUTO' AND r.control_generation=s.generation AND r.state IN"
                + " ('QUEUED','PROCESSING','FAILED'))",
            Boolean.class,
            run,
            conversation));
  }

  public Context context(UUID conversation, String source) {
    return jdbc.query(
            "SELECT s.*,u.display_name assigned_name,c.source,c.state"
                + " request_state,c.summary,c.patient_id,c.patient_name,c.appointment_id FROM"
                + " agent_supervision s LEFT JOIN user_account u ON u.id=s.assigned_user_id LEFT"
                + " JOIN agent_request_context c ON c.conversation_id=s.conversation_id AND"
                + " c.source=? WHERE s.conversation_id=?",
            (r, i) ->
                new Context(
                    conversation,
                    r.getString("mode"),
                    r.getLong("generation"),
                    r.getObject("assigned_user_id", UUID.class),
                    r.getString("assigned_name"),
                    r.getString("reason"),
                    source,
                    r.getString("request_state"),
                    r.getString("summary"),
                    r.getObject("patient_id", UUID.class),
                    r.getString("patient_name"),
                    r.getObject("appointment_id", UUID.class),
                    r.getTimestamp("updated_at").toInstant()),
            source,
            conversation)
        .getFirst();
  }

  public void request(
      UUID conversation,
      String source,
      String state,
      String summary,
      UUID patient,
      String name,
      UUID appointment,
      Instant now) {
    jdbc.update(
        "UPDATE agent_request_context SET"
            + " state=?,summary=?,patient_id=coalesce(?,patient_id),patient_name=CASE WHEN ?=''"
            + " THEN patient_name ELSE ? END,appointment_id=coalesce(?,appointment_id),updated_at=?"
            + " WHERE conversation_id=? AND source=?",
        state,
        summary,
        patient,
        name,
        name,
        appointment,
        Timestamp.from(now),
        conversation,
        source);
  }

  public void verify(
      UUID conversation,
      String source,
      UUID patient,
      String name,
      String relationship,
      Instant now) {
    jdbc.update(
        "UPDATE agent_request_context SET"
            + " patient_id=?,patient_name=?,relationship=?,verified_at=?,appointment_id=NULL,updated_at=?"
            + " WHERE conversation_id=? AND source=?",
        patient,
        name,
        relationship,
        Timestamp.from(now),
        Timestamp.from(now),
        conversation,
        source);
  }

  public Optional<Map<String, Object>> verified(UUID conversation, String source, Instant now) {
    return jdbc
        .queryForList(
            "SELECT * FROM agent_request_context WHERE conversation_id=? AND source=? AND"
                + " verified_at>?",
            conversation,
            source,
            Timestamp.from(now.minusSeconds(86400)))
        .stream()
        .findFirst();
  }

  public void outcome(UUID run, String outcome) {
    jdbc.update("UPDATE agent_run SET operational_result=? WHERE id=?", outcome, run);
  }

  public Map<String, Object> metadata(UUID run) {
    return jdbc.queryForMap(
        "SELECT provider,model,flow_version,operational_result FROM agent_run WHERE id=?", run);
  }

  public void responded(UUID run) {
    jdbc.update(
        "UPDATE agent_run SET operational_result='RESPONDED' WHERE id=? AND"
            + " operational_result='PENDING'",
        run);
  }

  public void expire(Instant now) {
    jdbc.update(
        "UPDATE agent_proposal SET state='EXPIRED' WHERE state='PENDING' AND expires_at<=?",
        Timestamp.from(now));
    jdbc.update(
        "UPDATE agent_change_proposal SET state='EXPIRED' WHERE state='PENDING' AND expires_at<=?",
        Timestamp.from(now));
    jdbc.update(
        "UPDATE agent_request_context c SET state='EXPIRED' WHERE c.state='CONFIRMATION_PENDING'"
            + " AND NOT EXISTS(SELECT 1 FROM agent_proposal p WHERE"
            + " p.conversation_id=c.conversation_id AND p.source=c.source AND p.state='PENDING')"
            + " AND NOT EXISTS(SELECT 1 FROM agent_change_proposal p WHERE"
            + " p.conversation_id=c.conversation_id AND p.source=c.source AND p.state='PENDING')");
  }

  public void suppressNotices(UUID conversation) {
    jdbc.update(
        "UPDATE kapso_message SET"
            + " status='FAILED',error_code='HUMAN_CONTROL',error_message='Recepción asumió la"
            + " conversación antes del envío.' WHERE conversation_id=? AND source='AGENT' AND"
            + " status='QUEUED'",
        conversation);
  }

  public boolean replyAllowed(UUID reply) {
    return Boolean.TRUE.equals(
        jdbc.queryForObject(
            "SELECT EXISTS(SELECT 1 FROM agent_run r JOIN agent_supervision s ON"
                + " s.conversation_id=r.conversation_id JOIN kapso_message m ON"
                + " m.id=r.reply_message_id WHERE m.id=? AND ((s.mode='AUTO' AND"
                + " s.generation=r.control_generation) OR (s.mode='HANDOFF' AND"
                + " m.error_code='HANDOFF_NOTICE' AND s.generation=r.control_generation+1)))",
            Boolean.class,
            reply));
  }
}
