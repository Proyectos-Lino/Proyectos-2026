package com.odontocare.kapso.repository;

import com.odontocare.shared.pagination.*;
import com.odontocare.whatsapp.model.*;
import java.sql.*;
import java.time.Instant;
import java.util.*;
import org.springframework.data.domain.PageImpl;
import org.springframework.jdbc.core.*;
import org.springframework.stereotype.Repository;

@Repository
public class KapsoRepository {
  private final JdbcTemplate jdbc;

  public KapsoRepository(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  private static final RowMapper<WhatsAppConversation> CONVERSATION =
      (r, i) ->
          new WhatsAppConversation(
              r.getObject("id", UUID.class),
              r.getString("phone"),
              r.getString("contact_name"),
              r.getTimestamp("last_message_at").toInstant(),
              instant(r, "last_inbound_at"),
              r.getString("last_message_preview"));
  private static final RowMapper<WhatsAppMessage> MESSAGE =
      (r, i) ->
          new WhatsAppMessage(
              r.getObject("id", UUID.class),
              r.getObject("conversation_id", UUID.class),
              r.getString("direction"),
              r.getString("kind"),
              r.getString("body"),
              r.getString("provider_sid"),
              r.getString("status"),
              r.getTimestamp("created_at").toInstant(),
              r.getString("error_code"),
              r.getString("error_message"),
              r.getInt("attempts"),
              r.getObject("request_key", UUID.class),
              null,
              r.getString("source"));

  private static Instant instant(ResultSet result, String name) throws SQLException {
    var time = result.getTimestamp(name);
    return time == null ? null : time.toInstant();
  }

  private String search(String text) {
    return "%"
        + text.toLowerCase(Locale.ROOT).replace("!", "!!").replace("%", "!%").replace("_", "!_")
        + "%";
  }

  public PageResponse<WhatsAppConversation> conversations(String number, PageQuery query) {
    var page =
        query.pageable(
            Map.of("name", "contact_name", "lastMessageAt", "last_message_at", "phone", "phone"));
    String where =
        " WHERE phone_number_id=? AND (lower(phone) LIKE ? ESCAPE '!' OR lower(contact_name) LIKE ?"
            + " ESCAPE '!' OR lower(last_message_preview) LIKE ? ESCAPE '!')";
    var args =
        new ArrayList<Object>(
            List.of(
                number,
                search(query.getSearch()),
                search(query.getSearch()),
                search(query.getSearch())));
    long count =
        jdbc.queryForObject(
            "SELECT count(*) FROM kapso_conversation" + where, Long.class, args.toArray());
    args.add(page.getPageSize());
    args.add(page.getOffset());
    String order =
        page.getSort().stream()
            .map(o -> o.getProperty() + " " + o.getDirection())
            .reduce((a, b) -> a + "," + b)
            .orElseThrow();
    return PageResponse.of(
        new PageImpl<>(
            jdbc.query(
                "SELECT * FROM kapso_conversation"
                    + where
                    + " ORDER BY "
                    + order
                    + " LIMIT ? OFFSET ?",
                CONVERSATION,
                args.toArray()),
            page,
            count));
  }

  public Optional<WhatsAppConversation> conversation(UUID id, boolean lock) {
    return jdbc
        .query(
            "SELECT * FROM kapso_conversation WHERE id=?" + (lock ? " FOR UPDATE" : ""),
            CONVERSATION,
            id)
        .stream()
        .findFirst();
  }

  public boolean belongsTo(UUID conversation, String number) {
    return Boolean.TRUE.equals(
        jdbc.queryForObject(
            "SELECT EXISTS(SELECT 1 FROM kapso_conversation WHERE id=? AND phone_number_id=?)",
            Boolean.class,
            conversation,
            number));
  }

  public UUID inboundConversation(String number, String phone, String name, Instant now) {
    jdbc.update(
        "INSERT INTO kapso_conversation(id,phone_number_id,phone,contact_name,last_message_at)"
            + " VALUES(?,?,?,?,?) ON CONFLICT(phone_number_id,phone) DO NOTHING",
        UUID.randomUUID(),
        number,
        phone,
        name,
        Timestamp.from(now));
    UUID id =
        jdbc.queryForObject(
            "SELECT id FROM kapso_conversation WHERE phone_number_id=? AND phone=? FOR UPDATE",
            UUID.class,
            number,
            phone);
    if (!name.isBlank())
      jdbc.update("UPDATE kapso_conversation SET contact_name=? WHERE id=?", name, id);
    return id;
  }

  public boolean inbound(
      UUID id, UUID conversation, String sid, String body, String kind, Instant now) {
    return jdbc.update(
            "INSERT INTO"
                + " kapso_message(id,conversation_id,direction,kind,body,provider_sid,status,created_at,updated_at,next_attempt_at)"
                + " VALUES(?,?,'INBOUND',?,?,?,?,?,?,?) ON CONFLICT(provider_sid) DO NOTHING",
            id,
            conversation,
            kind,
            body,
            sid,
            kind.equals("TEXT") ? "RECEIVED" : "UNSUPPORTED",
            Timestamp.from(now),
            Timestamp.from(now),
            Timestamp.from(now))
        == 1;
  }

  public void touch(UUID id, String body, Instant time, boolean incoming) {
    String preview =
        body.substring(
            0, body.offsetByCodePoints(0, Math.min(200, body.codePointCount(0, body.length()))));
    var conversation = conversation(id, false).orElseThrow();
    if (!time.isBefore(conversation.lastMessageAt()))
      jdbc.update(
          "UPDATE kapso_conversation SET last_message_at=?,last_message_preview=? WHERE id=?",
          Timestamp.from(time),
          preview,
          id);
    if (incoming)
      jdbc.update(
          "UPDATE kapso_conversation SET last_inbound_at=greatest(last_inbound_at,?) WHERE id=?",
          Timestamp.from(time),
          id);
  }

  public PageResponse<WhatsAppMessage> messages(
      UUID id, PageQuery query, String direction, String status) {
    var page = query.pageable(Map.of("name", "created_at", "createdAt", "created_at"));
    String where =
        " WHERE conversation_id=? AND (lower(body) LIKE ? ESCAPE '!' OR"
            + " lower(coalesce(provider_sid,'')) LIKE ? ESCAPE '!')";
    var args =
        new ArrayList<Object>(List.of(id, search(query.getSearch()), search(query.getSearch())));
    if (direction != null && !direction.isBlank()) {
      where += " AND direction=?";
      args.add(direction);
    }
    if (status != null && !status.isBlank()) {
      where += " AND status=?";
      args.add(status);
    }
    long count =
        jdbc.queryForObject(
            "SELECT count(*) FROM kapso_message" + where, Long.class, args.toArray());
    args.add(page.getPageSize());
    args.add(page.getOffset());
    return PageResponse.of(
        new PageImpl<>(
            jdbc.query(
                "SELECT * FROM kapso_message"
                    + where
                    + " ORDER BY created_at "
                    + query.getDirection()
                    + ",id LIMIT ? OFFSET ?",
                MESSAGE,
                args.toArray()),
            page,
            count));
  }

  public Optional<WhatsAppMessage> message(UUID id, boolean lock) {
    return jdbc
        .query("SELECT * FROM kapso_message WHERE id=?" + (lock ? " FOR UPDATE" : ""), MESSAGE, id)
        .stream()
        .findFirst();
  }

  public Optional<WhatsAppMessage> bySid(String sid) {
    return jdbc
        .query("SELECT * FROM kapso_message WHERE provider_sid=? FOR UPDATE", MESSAGE, sid)
        .stream()
        .findFirst();
  }

  public Optional<WhatsAppMessage> byKey(UUID key) {
    return jdbc.query("SELECT * FROM kapso_message WHERE request_key=?", MESSAGE, key).stream()
        .findFirst();
  }

  public void keyLock(UUID key) {
    jdbc.queryForObject("SELECT pg_advisory_xact_lock(hashtext(?))", Object.class, "kapso:" + key);
  }

  public void referenceLock(String reference) {
    jdbc.queryForObject(
        "SELECT pg_advisory_xact_lock(hashtext(?))", Object.class, "kapso-reference:" + reference);
  }

  public WhatsAppMessage enqueue(UUID conversation, String body, UUID key, Instant now) {
    return enqueue(conversation, body, key, now, "KAPSO");
  }

  private WhatsAppMessage enqueue(
      UUID conversation, String body, UUID key, Instant now, String source) {
    UUID id = UUID.randomUUID();
    jdbc.update(
        "INSERT INTO"
            + " kapso_message(id,conversation_id,direction,kind,body,request_key,status,created_at,updated_at,next_attempt_at,source)"
            + " VALUES(?,?,'OUTBOUND','TEXT',?,?,'QUEUED',?,?,?,?)",
        id,
        conversation,
        body,
        key,
        Timestamp.from(now),
        Timestamp.from(now),
        Timestamp.from(now),
        source);
    return message(id, false).orElseThrow();
  }

  public WhatsAppMessage enqueueAgent(UUID conversation, String body, UUID key, Instant now) {
    return enqueue(conversation, body, key, now, "AGENT");
  }

  public UUID testInbound(UUID conversation, String body, UUID key, Instant now) {
    UUID id = UUID.randomUUID();
    jdbc.update(
        "INSERT INTO"
            + " kapso_message(id,conversation_id,direction,kind,body,request_key,status,created_at,updated_at,next_attempt_at,source)"
            + " VALUES(?,?,'INBOUND','TEXT',?,?,'RECEIVED',?,?,?,'APP_TEST')",
        id,
        conversation,
        body,
        key,
        Timestamp.from(now),
        Timestamp.from(now),
        Timestamp.from(now));
    touch(conversation, body, now, false);
    return id;
  }

  public void retryReply(UUID id, Instant now) {
    jdbc.update(
        "UPDATE kapso_message SET"
            + " status='QUEUED',next_attempt_at=?,updated_at=?,error_code=NULL,error_message=NULL"
            + " WHERE id=?",
        Timestamp.from(now),
        Timestamp.from(now),
        id);
  }

  public void markHandoffNotice(UUID run) {
    jdbc.update(
        "UPDATE kapso_message SET error_code='HANDOFF_NOTICE' WHERE id=(SELECT reply_message_id"
            + " FROM agent_run WHERE id=?)",
        run);
  }

  public Optional<WhatsAppMessage> claim(String number, Instant now) {
    jdbc.update(
        "UPDATE kapso_message SET status='UNKNOWN',error_message='El envío quedó interrumpido;"
            + " revisa Kapso antes de repetirlo.',updated_at=? WHERE status='SENDING' AND"
            + " updated_at<?",
        Timestamp.from(now),
        Timestamp.from(now.minusSeconds(60)));
    var rows =
        jdbc.query(
            "SELECT m.* FROM kapso_message m JOIN kapso_conversation c ON c.id=m.conversation_id"
                + " WHERE m.status='QUEUED' AND m.next_attempt_at<=? AND c.phone_number_id=? ORDER"
                + " BY m.created_at,m.id LIMIT 1 FOR UPDATE OF m SKIP LOCKED",
            MESSAGE,
            Timestamp.from(now),
            number);
    if (rows.isEmpty()) return Optional.empty();
    UUID id = rows.getFirst().id();
    jdbc.update(
        "UPDATE kapso_message SET status='SENDING',attempts=attempts+1,updated_at=? WHERE id=?",
        Timestamp.from(now),
        id);
    return message(id, false);
  }

  public void updateDelivery(
      UUID id, String reference, String state, String code, String detail, Instant now) {
    jdbc.update(
        "UPDATE kapso_message SET"
            + " provider_sid=coalesce(provider_sid,?),status=?,error_code=?,error_message=?,updated_at=?"
            + " WHERE id=?",
        reference,
        state,
        code,
        detail,
        Timestamp.from(now),
        id);
  }

  public void retry(UUID id, Instant now) {
    jdbc.update(
        "UPDATE kapso_message SET"
            + " status='QUEUED',next_attempt_at=?,updated_at=?,error_code='RATE_LIMIT',error_message='Kapso"
            + " pidió esperar; reintento pendiente.' WHERE id=?",
        Timestamp.from(now.plusSeconds(10)),
        Timestamp.from(now),
        id);
  }

  public boolean webhook(
      String key,
      String hash,
      String event,
      String number,
      String reference,
      String phone,
      String state,
      String code,
      Instant now) {
    int inserted =
        jdbc.update(
            "INSERT INTO"
                + " kapso_webhook_event(delivery_key,payload_hash,event,phone_number_id,message_reference,recipient_phone,state,error_code,received_at)"
                + " VALUES(?,?,?,?,?,?,?,?,?) ON CONFLICT(delivery_key) DO NOTHING",
            key,
            hash,
            event,
            number,
            reference,
            phone,
            state,
            code == null ? "" : code,
            Timestamp.from(now));
    if (inserted == 0) {
      var old =
          jdbc.queryForMap(
              "SELECT payload_hash,event FROM kapso_webhook_event WHERE delivery_key=?", key);
      if (!old.get("payload_hash").equals(hash) || !old.get("event").equals(event))
        throw com.odontocare.shared.web.ApiException.conflict(
            "La referencia del evento ya pertenece a otro contenido.");
    }
    return inserted == 1;
  }

  public List<Map<String, Object>> deliveryReceipts(String number, String reference, String phone) {
    return jdbc.queryForList(
        "SELECT state,error_code FROM kapso_webhook_event WHERE phone_number_id=? AND"
            + " message_reference=? AND recipient_phone=? AND state IN"
            + " ('SENT','DELIVERED','READ','FAILED') ORDER BY received_at,delivery_key LIMIT 100",
        number,
        reference,
        phone);
  }
}
