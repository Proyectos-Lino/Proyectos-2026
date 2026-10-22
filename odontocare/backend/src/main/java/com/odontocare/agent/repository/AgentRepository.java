package com.odontocare.agent.repository;

import com.odontocare.agent.dto.AgentContracts.*;
import com.odontocare.agent.model.AgentModelCheckpoint;
import com.odontocare.agent.model.AgentRun;
import com.odontocare.shared.pagination.*;
import java.sql.*;
import java.time.*;
import java.util.*;
import org.springframework.data.domain.PageImpl;
import org.springframework.jdbc.core.*;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.ObjectMapper;

@Repository
public class AgentRepository {
  private final JdbcTemplate jdbc;
  private final ObjectMapper mapper;

  public AgentRepository(JdbcTemplate jdbc, ObjectMapper mapper) {
    this.jdbc = jdbc;
    this.mapper = mapper;
  }

  public boolean hasRejectedBookingLookup(UUID run) {
    return Boolean.TRUE.equals(
        jdbc.queryForObject(
            "SELECT EXISTS(SELECT 1 FROM agent_step WHERE run_id=? AND kind='TOOL' AND"
                + " state='REJECTED' AND name IN"
                + " ('consultar_servicios','consultar_horarios','proponer_cita'))",
            Boolean.class,
            run));
  }

  private static final RowMapper<AgentRun> RUN =
      (r, i) ->
          new AgentRun(
              r.getObject("id", UUID.class),
              r.getObject("message_id", UUID.class),
              r.getObject("conversation_id", UUID.class),
              r.getString("state"),
              r.getString("model"),
              r.getString("response_text"),
              r.getString("error_code"),
              r.getString("error_message"),
              r.getInt("attempts"),
              r.getInt("input_tokens"),
              r.getInt("output_tokens"),
              r.getTimestamp("created_at").toInstant(),
              r.getTimestamp("updated_at").toInstant());
  private static final RowMapper<Slot> SLOT =
      (r, i) ->
          new Slot(
              r.getObject("id", UUID.class),
              r.getObject("run_id", UUID.class),
              r.getObject("conversation_id", UUID.class),
              r.getObject("dentist_id", UUID.class),
              r.getObject("service_id", UUID.class),
              r.getTimestamp("local_start").toLocalDateTime(),
              r.getInt("duration_minutes"),
              r.getString("time_zone"),
              r.getTimestamp("expires_at").toInstant(),
              r.getString("service_name"),
              r.getString("dentist_name"));
  private static final RowMapper<Proposal> PROPOSAL =
      (r, i) ->
          new Proposal(
              r.getObject("id", UUID.class),
              r.getObject("run_id", UUID.class),
              r.getObject("conversation_id", UUID.class),
              r.getObject("slot_id", UUID.class),
              r.getObject("patient_id", UUID.class),
              r.getString("patient_name"),
              r.getString("summary"),
              r.getString("confirmation_code"),
              r.getString("state"),
              r.getObject("appointment_id", UUID.class),
              r.getObject("confirmation_message_id", UUID.class),
              r.getTimestamp("created_at").toInstant(),
              r.getTimestamp("expires_at").toInstant());

  public AgentRun enqueue(UUID message, UUID conversation, String model, Instant now) {
    jdbc.update(
        "INSERT INTO"
            + " agent_run(id,message_id,conversation_id,state,model,created_at,updated_at,next_attempt_at)"
            + " VALUES(?,?,?,'QUEUED',?,?,?,?) ON CONFLICT(message_id) DO NOTHING",
        UUID.randomUUID(),
        message,
        conversation,
        model,
        Timestamp.from(now),
        Timestamp.from(now),
        Timestamp.from(now));
    return jdbc.query("SELECT * FROM agent_run WHERE message_id=?", RUN, message).getFirst();
  }

  public Optional<AgentRun> get(UUID id, boolean lock) {
    return jdbc
        .query("SELECT * FROM agent_run WHERE id=?" + (lock ? " FOR UPDATE" : ""), RUN, id)
        .stream()
        .findFirst();
  }

  public Optional<AgentRun> claim(Instant now, String provider, int debounce) {
    jdbc.update(
        "UPDATE agent_run SET state=CASE WHEN attempts<3 THEN 'QUEUED' ELSE 'FAILED'"
            + " END,error_code='INTERRUPTED',error_message='El proceso quedó interrumpido; se"
            + " recupera sin duplicar la cita.',updated_at=? WHERE state='PROCESSING' AND"
            + " updated_at<?",
        Timestamp.from(now),
        Timestamp.from(now.minusSeconds(300)));
    var rows =
        jdbc.query(
            "SELECT r.* FROM agent_run r JOIN agent_conversation_source c ON c.id=r.conversation_id"
                + " JOIN agent_inbox_message current_input ON current_input.id=r.message_id WHERE"
                + " c.provider=? AND r.state='QUEUED' AND NOT EXISTS(SELECT 1 FROM"
                + " agent_supervision supervision WHERE"
                + " supervision.conversation_id=r.conversation_id AND supervision.mode<>'AUTO') AND"
                + " r.next_attempt_at<=? AND r.created_at<=? AND NOT EXISTS(SELECT 1 FROM agent_run"
                + " x WHERE x.conversation_id=r.conversation_id AND (x.state='PROCESSING' OR"
                + " (x.state='QUEUED' AND x.sequence_no>r.sequence_no AND EXISTS(SELECT 1 FROM"
                + " agent_inbox_message xm WHERE xm.id=x.message_id AND"
                + " xm.source=current_input.source)))) ORDER BY r.sequence_no LIMIT 1 FOR UPDATE OF"
                + " r SKIP LOCKED",
            RUN,
            provider,
            Timestamp.from(now),
            Timestamp.from(now.minusMillis(debounce)));
    if (rows.isEmpty()) return Optional.empty();
    var run = rows.getFirst();
    jdbc.update(
        "UPDATE agent_run SET state='GROUPED',grouped_into_run_id=?,updated_at=? WHERE"
            + " conversation_id=? AND state='QUEUED' AND sequence_no<? AND message_id IN (SELECT id"
            + " FROM agent_inbox_message WHERE source=(SELECT source FROM agent_inbox_message WHERE"
            + " id=?))",
        run.id(),
        Timestamp.from(now),
        run.conversationId(),
        jdbc.queryForObject("SELECT sequence_no FROM agent_run WHERE id=?", Long.class, run.id()),
        run.messageId());
    jdbc.update(
        "UPDATE agent_run SET"
            + " state='PROCESSING',attempts=attempts+1,error_code=NULL,error_message=NULL,updated_at=?"
            + " WHERE id=?",
        Timestamp.from(now),
        run.id());
    return get(run.id(), false);
  }

  public void finish(UUID id, String state, String text, String code, String detail, Instant now) {
    jdbc.update(
        "UPDATE agent_run SET"
            + " state=?,response_text=?,error_code=?,error_message=?,updated_at=?,model_checkpoint=NULL"
            + " WHERE id=?",
        state,
        text,
        code,
        detail,
        Timestamp.from(now),
        id);
  }

  public void retry(UUID id, Instant now) {
    jdbc.update(
        "UPDATE agent_run SET"
            + " state='QUEUED',error_code=NULL,error_message=NULL,model_checkpoint=NULL,updated_at=?,next_attempt_at=?"
            + " WHERE id=?",
        Timestamp.from(now),
        Timestamp.from(now),
        id);
  }

  public void usage(UUID id, int input, int output) {
    jdbc.update(
        "UPDATE agent_run SET input_tokens=input_tokens+?,output_tokens=output_tokens+? WHERE id=?",
        input,
        output,
        id);
  }

  public Optional<AgentModelCheckpoint> checkpoint(UUID id) {
    return jdbc
        .query(
            "SELECT model_checkpoint FROM agent_run WHERE id=? AND model_checkpoint IS NOT NULL",
            (r, i) -> mapper.readValue(r.getString(1), AgentModelCheckpoint.class),
            id)
        .stream()
        .findFirst();
  }

  public void checkpoint(UUID id, AgentModelCheckpoint checkpoint, Instant now) {
    jdbc.update(
        "UPDATE agent_run SET model_checkpoint=?::jsonb,updated_at=? WHERE id=? AND"
            + " state='PROCESSING'",
        mapper.writeValueAsString(checkpoint),
        Timestamp.from(now),
        id);
  }

  public void step(
      UUID run,
      String kind,
      String name,
      Object arguments,
      Object result,
      String state,
      Instant now) {
    jdbc.update(
        "INSERT INTO"
            + " agent_step(id,run_id,ordinal,kind,name,arguments_json,result_json,state,created_at)"
            + " SELECT ?,?,coalesce(max(ordinal),0)+1,?,?,?::jsonb,?::jsonb,?,? FROM agent_step"
            + " WHERE run_id=?",
        UUID.randomUUID(),
        run,
        kind,
        name,
        mapper.writeValueAsString(arguments),
        mapper.writeValueAsString(result),
        state,
        Timestamp.from(now),
        run);
  }

  public PageResponse<AgentRun> runs(UUID conversation, PageQuery query, String state) {
    var pageable = query.pageable(Map.of("name", "createdAt", "createdAt", "createdAt"));
    String where = " WHERE r.conversation_id=?";
    var args = new ArrayList<Object>();
    args.add(conversation);
    if (state != null && !state.isBlank()) {
      where += " AND r.state=?";
      args.add(state);
    }
    if (!query.getSearch().isEmpty()) {
      where +=
          " AND (lower(m.body) LIKE ? ESCAPE '!' OR lower(r.response_text) LIKE ? ESCAPE '!' OR"
              + " lower(coalesce(r.error_message,'')) LIKE ? ESCAPE '!')";
      String search = pattern(query.getSearch());
      args.add(search);
      args.add(search);
      args.add(search);
    }
    String join = " FROM agent_run r JOIN agent_inbox_message m ON m.id=r.message_id";
    long count = jdbc.queryForObject("SELECT count(*)" + join + where, Long.class, args.toArray());
    args.add(pageable.getPageSize());
    args.add(pageable.getOffset());
    return PageResponse.of(
        new PageImpl<>(
            jdbc.query(
                "SELECT r.*"
                    + join
                    + where
                    + " ORDER BY r.created_at "
                    + query.getDirection()
                    + ",r.id LIMIT ? OFFSET ?",
                RUN,
                args.toArray()),
            pageable,
            count));
  }

  public PageResponse<Step> steps(UUID run, PageQuery query, String kind) {
    var pageable = query.pageable(Map.of("name", "ordinal", "ordinal", "ordinal"));
    var args = new ArrayList<Object>();
    args.add(run);
    String where = " WHERE run_id=?";
    if (kind != null && !kind.isBlank()) {
      where += " AND kind=?";
      args.add(kind);
    }
    if (!query.getSearch().isEmpty()) {
      where += " AND (lower(name) LIKE ? ESCAPE '!' OR lower(result_json::text) LIKE ? ESCAPE '!')";
      args.add(pattern(query.getSearch()));
      args.add(pattern(query.getSearch()));
    }
    long count =
        jdbc.queryForObject("SELECT count(*) FROM agent_step" + where, Long.class, args.toArray());
    args.add(pageable.getPageSize());
    args.add(pageable.getOffset());
    return PageResponse.of(
        new PageImpl<>(
            jdbc.query(
                "SELECT * FROM agent_step"
                    + where
                    + " ORDER BY ordinal "
                    + query.getDirection()
                    + " LIMIT ? OFFSET ?",
                (r, i) ->
                    new Step(
                        r.getObject("id", UUID.class),
                        r.getInt("ordinal"),
                        r.getString("kind"),
                        r.getString("name"),
                        mapper.readTree(r.getString("arguments_json")),
                        mapper.readTree(r.getString("result_json")),
                        r.getString("state"),
                        r.getTimestamp("created_at").toInstant()),
                args.toArray()),
            pageable,
            count));
  }

  private String pattern(String text) {
    return "%"
        + text.toLowerCase(Locale.ROOT).replace("!", "!!").replace("%", "!%").replace("_", "!_")
        + "%";
  }

  public List<Map<String, Object>> context(UUID conversation, UUID message, int limit) {
    // Use only messages preceding this event. Later inbound events have their own ordered run.
    var rows =
        jdbc.queryForList(
            "SELECT m.body,m.created_at,CASE WHEN m.source='APP_TEST' OR m.provider='TWILIO' OR"
                + " reply.status IN ('SENT','DELIVERED','READ') THEN r.response_text ELSE NULL END"
                + " response_text FROM agent_inbox_message m LEFT JOIN agent_run r ON"
                + " r.message_id=m.id AND r.state IN ('COMPLETED','FAILED') LEFT JOIN kapso_message"
                + " reply ON reply.id=r.reply_message_id WHERE m.conversation_id=? AND"
                + " m.direction='INBOUND' AND m.kind='TEXT' AND m.sequence_no<=(SELECT sequence_no"
                + " FROM agent_inbox_message WHERE id=?) AND m.source=(SELECT source FROM"
                + " agent_inbox_message WHERE id=?) ORDER BY m.sequence_no DESC LIMIT ?",
            conversation,
            message,
            message,
            limit);
    Collections.reverse(rows);
    var result = new ArrayList<Map<String, Object>>();
    for (var row : rows) {
      result.add(
          Map.of(
              "role",
              "user",
              "content",
              "[Recibido "
                  + ((Timestamp) row.get("created_at")).toInstant()
                  + "] "
                  + row.get("body")));
      Object response = row.get("response_text");
      if (response != null && !response.toString().isBlank())
        result.add(Map.of("role", "assistant", "content", response));
    }
    return result;
  }

  public UUID latestInbound(UUID conversation) {
    var ids =
        jdbc.queryForList(
            "SELECT id FROM agent_inbox_message WHERE conversation_id=? AND direction='INBOUND'"
                + " ORDER BY sequence_no DESC LIMIT 1",
            UUID.class,
            conversation);
    return ids.isEmpty() ? null : ids.getFirst();
  }

  public void attachReply(UUID run, UUID reply) {
    jdbc.update("UPDATE agent_run SET reply_message_id=? WHERE id=?", reply, run);
  }

  public UUID latestInbound(UUID conversation, UUID current) {
    var ids =
        jdbc.queryForList(
            "SELECT id FROM agent_inbox_message WHERE conversation_id=? AND direction='INBOUND' AND"
                + " source=(SELECT source FROM agent_inbox_message WHERE id=?) ORDER BY sequence_no"
                + " DESC LIMIT 1",
            UUID.class,
            conversation,
            current);
    return ids.isEmpty() ? null : ids.getFirst();
  }

  public void group(UUID run, UUID target, Instant now) {
    jdbc.update(
        "UPDATE agent_run SET state='GROUPED',grouped_into_run_id=?,updated_at=? WHERE id=?",
        target,
        Timestamp.from(now),
        run);
  }

  public void scheduleRetry(UUID run, Instant time, String code) {
    jdbc.update(
        "UPDATE agent_run SET"
            + " state='QUEUED',next_attempt_at=?,error_code=?,error_message='Fallo temporal del"
            + " proveedor; continuación automática pendiente.' WHERE id=?",
        Timestamp.from(time),
        code,
        run);
  }

  public boolean naturalConfirmationAllowed(UUID proposalRun, UUID currentMessage) {
    return Boolean.TRUE.equals(
        jdbc.queryForObject(
            "SELECT EXISTS(SELECT 1 FROM agent_run r JOIN agent_inbox_message incoming ON"
                + " incoming.id=? JOIN agent_inbox_message original ON original.id=r.message_id"
                + " LEFT JOIN kapso_message reply ON reply.id=r.reply_message_id WHERE r.id=? AND"
                + " incoming.conversation_id=r.conversation_id AND incoming.source=original.source"
                + " AND r.state='COMPLETED' AND (incoming.source='APP_TEST' OR (reply.status IN"
                + " ('SENT','DELIVERED','READ') AND"
                + " incoming.created_at>=date_trunc('second',reply.created_at))))",
            Boolean.class,
            currentMessage,
            proposalRun));
  }

  public Slot addSlot(
      UUID run,
      UUID conversation,
      UUID dentist,
      UUID service,
      LocalDateTime start,
      int duration,
      String zone,
      Instant expires,
      String serviceName,
      String dentistName) {
    UUID id = UUID.randomUUID();
    jdbc.update(
        "INSERT INTO"
            + " agent_slot(id,run_id,conversation_id,dentist_id,service_id,local_start,duration_minutes,time_zone,expires_at,service_name,dentist_name)"
            + " VALUES(?,?,?,?,?,?,?,?,?,?,?)",
        id,
        run,
        conversation,
        dentist,
        service,
        Timestamp.valueOf(start),
        duration,
        zone,
        Timestamp.from(expires),
        serviceName,
        dentistName);
    return slot(id).orElseThrow();
  }

  public Optional<Slot> slot(UUID id) {
    return jdbc.query("SELECT * FROM agent_slot WHERE id=?", SLOT, id).stream().findFirst();
  }

  public Optional<Proposal> currentProposal(UUID conversation) {
    return jdbc
        .query(
            "SELECT p.* FROM agent_proposal p JOIN agent_run r ON r.id=p.run_id WHERE"
                + " p.conversation_id=? ORDER BY r.sequence_no DESC LIMIT 1",
            PROPOSAL,
            conversation)
        .stream()
        .findFirst();
  }

  public Optional<Proposal> proposalByRun(UUID run) {
    return jdbc.query("SELECT * FROM agent_proposal WHERE run_id=?", PROPOSAL, run).stream()
        .findFirst();
  }

  public Optional<Proposal> currentProposal(UUID conversation, String source) {
    return jdbc
        .query(
            "SELECT p.* FROM agent_proposal p JOIN agent_run r ON r.id=p.run_id JOIN"
                + " agent_inbox_message m ON m.id=r.message_id WHERE p.conversation_id=? AND"
                + " m.source=? ORDER BY r.sequence_no DESC LIMIT 1",
            PROPOSAL,
            conversation,
            source)
        .stream()
        .findFirst();
  }

  public Optional<Proposal> proposalByCode(UUID conversation, String code) {
    return jdbc
        .query(
            "SELECT * FROM agent_proposal WHERE conversation_id=? AND confirmation_code=? FOR"
                + " UPDATE",
            PROPOSAL,
            conversation,
            code)
        .stream()
        .findFirst();
  }

  public Proposal propose(
      UUID run,
      UUID conversation,
      UUID slot,
      UUID patient,
      String name,
      String summary,
      Instant now) {
    jdbc.update(
        "UPDATE agent_proposal SET state='SUPERSEDED' WHERE conversation_id=? AND state='PENDING'"
            + " AND run_id IN (SELECT prior.id FROM agent_run prior JOIN agent_inbox_message m ON"
            + " m.id=prior.message_id WHERE m.source=(SELECT incoming.source FROM agent_run"
            + " current_run JOIN agent_inbox_message incoming ON incoming.id=current_run.message_id"
            + " WHERE current_run.id=?))",
        conversation,
        run);
    UUID id = UUID.randomUUID();
    String code = id.toString().replace("-", "").substring(0, 8).toUpperCase(Locale.ROOT);
    jdbc.update(
        "INSERT INTO"
            + " agent_proposal(id,run_id,conversation_id,slot_id,patient_id,patient_name,summary,confirmation_code,state,created_at,expires_at)"
            + " VALUES(?,?,?,?,?,?,?,?,'PENDING',?,?)",
        id,
        run,
        conversation,
        slot,
        patient,
        name,
        summary,
        code,
        Timestamp.from(now),
        Timestamp.from(now.plusSeconds(1800)));
    return proposalByRun(run).orElseThrow();
  }

  public void proposalState(UUID id, String state) {
    jdbc.update("UPDATE agent_proposal SET state=? WHERE id=?", state, id);
  }

  public void confirmed(UUID id, UUID appointment, UUID message) {
    jdbc.update(
        "UPDATE agent_proposal SET state='CONFIRMED',appointment_id=?,confirmation_message_id=?"
            + " WHERE id=?",
        appointment,
        message,
        id);
  }
}
