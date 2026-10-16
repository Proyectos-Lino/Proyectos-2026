package com.odontocare.whatsapp.repository;

import com.odontocare.shared.pagination.*;
import com.odontocare.whatsapp.model.*;
import java.sql.*;
import java.time.Instant;
import java.util.*;
import org.springframework.data.domain.PageImpl;
import org.springframework.jdbc.core.*;
import org.springframework.stereotype.Repository;

@Repository
public class WhatsAppRepository {
  private final JdbcTemplate jdbc;

  public WhatsAppRepository(JdbcTemplate jdbc) {
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
              r.getString("template_sid"),
              r.getString("source"));

  public UUID testInbound(UUID conversation, String body, UUID key, Instant now) {
    UUID id = UUID.randomUUID();
    jdbc.update(
        "INSERT INTO"
            + " whatsapp_message(id,conversation_id,direction,kind,body,request_key,status,created_at,updated_at,next_attempt_at,source)"
            + " VALUES(?,?,'INBOUND','TEXT',?,?,'RECEIVED',?,?,?,'APP_TEST')",
        id,
        conversation,
        body,
        key,
        Timestamp.from(now),
        Timestamp.from(now),
        Timestamp.from(now));
    touch(conversation, body, now, true);
    return id;
  }

  private static Instant instant(ResultSet r, String key) throws SQLException {
    var t = r.getTimestamp(key);
    return t == null ? null : t.toInstant();
  }

  private String search(String value) {
    return "%"
        + value.toLowerCase(Locale.ROOT).replace("!", "!!").replace("%", "!%").replace("_", "!_")
        + "%";
  }

  public PageResponse<WhatsAppConversation> conversations(PageQuery query) {
    var pageable =
        query.pageable(
            Map.of("name", "contact_name", "lastMessageAt", "last_message_at", "phone", "phone"));
    String where =
        " WHERE lower(phone) LIKE ? ESCAPE '!' OR lower(contact_name) LIKE ? ESCAPE '!' OR"
            + " lower(last_message_preview) LIKE ? ESCAPE '!'";
    String text = search(query.getSearch());
    var args = new ArrayList<Object>(List.of(text, text, text));
    long total =
        jdbc.queryForObject(
            "SELECT count(*) FROM whatsapp_conversation" + where, Long.class, args.toArray());
    args.add(pageable.getPageSize());
    args.add(pageable.getOffset());
    String order =
        pageable.getSort().stream()
            .map(o -> o.getProperty() + " " + o.getDirection())
            .reduce((a, b) -> a + "," + b)
            .orElseThrow();
    return PageResponse.of(
        new PageImpl<>(
            jdbc.query(
                "SELECT * FROM whatsapp_conversation"
                    + where
                    + " ORDER BY "
                    + order
                    + " LIMIT ? OFFSET ?",
                CONVERSATION,
                args.toArray()),
            pageable,
            total));
  }

  public PageResponse<WhatsAppMessage> messages(
      UUID id, PageQuery query, String direction, String status) {
    var pageable = query.pageable(Map.of("name", "created_at", "createdAt", "created_at"));
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
    long total =
        jdbc.queryForObject(
            "SELECT count(*) FROM whatsapp_message" + where, Long.class, args.toArray());
    args.add(pageable.getPageSize());
    args.add(pageable.getOffset());
    return PageResponse.of(
        new PageImpl<>(
            jdbc.query(
                "SELECT * FROM whatsapp_message"
                    + where
                    + " ORDER BY created_at "
                    + query.getDirection()
                    + ",id LIMIT ? OFFSET ?",
                MESSAGE,
                args.toArray()),
            pageable,
            total));
  }

  public Optional<WhatsAppConversation> conversation(UUID id, boolean lock) {
    return jdbc
        .query(
            "SELECT * FROM whatsapp_conversation WHERE id=?" + (lock ? " FOR UPDATE" : ""),
            CONVERSATION,
            id)
        .stream()
        .findFirst();
  }

  public UUID inboundConversation(String phone, String name, Instant now) {
    jdbc.update(
        "INSERT INTO whatsapp_conversation(id,phone,contact_name,last_message_at) VALUES(?,?,?,?)"
            + " ON CONFLICT(phone) DO NOTHING",
        UUID.randomUUID(),
        phone,
        name,
        Timestamp.from(now));
    return jdbc.queryForObject(
        "SELECT id FROM whatsapp_conversation WHERE phone=? FOR UPDATE", UUID.class, phone);
  }

  public boolean inbound(
      UUID id, UUID conversation, String sid, String body, String kind, Instant now) {
    return jdbc.update(
            "INSERT INTO"
                + " whatsapp_message(id,conversation_id,direction,kind,body,provider_sid,status,created_at,updated_at,next_attempt_at)"
                + " VALUES(?,?,'INBOUND',?,?,?, ?,?,?,?) ON CONFLICT(provider_sid) DO NOTHING",
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

  public void touch(UUID id, String body, Instant now, boolean inbound) {
    String preview =
        body.substring(
            0, body.offsetByCodePoints(0, Math.min(200, body.codePointCount(0, body.length()))));
    jdbc.update(
        "UPDATE whatsapp_conversation SET last_message_at=?,last_message_preview=?"
            + (inbound ? ",last_inbound_at=?" : "")
            + " WHERE id=?",
        inbound
            ? new Object[] {Timestamp.from(now), preview, Timestamp.from(now), id}
            : new Object[] {Timestamp.from(now), preview, id});
  }

  public Optional<WhatsAppMessage> byKey(UUID key) {
    return jdbc.query("SELECT * FROM whatsapp_message WHERE request_key=?", MESSAGE, key).stream()
        .findFirst();
  }

  public Optional<WhatsAppMessage> message(UUID id, boolean lock) {
    return jdbc
        .query(
            "SELECT * FROM whatsapp_message WHERE id=?" + (lock ? " FOR UPDATE" : ""), MESSAGE, id)
        .stream()
        .findFirst();
  }

  public Optional<WhatsAppMessage> bySid(String sid) {
    return jdbc
        .query("SELECT * FROM whatsapp_message WHERE provider_sid=? FOR UPDATE", MESSAGE, sid)
        .stream()
        .findFirst();
  }

  public void keyLock(UUID key) {
    jdbc.queryForObject("SELECT pg_advisory_xact_lock(hashtext(?))", Object.class, key.toString());
  }

  public WhatsAppMessage enqueue(
      UUID conversation, String body, String kind, UUID key, Instant now, String templateSid) {
    UUID id = UUID.randomUUID();
    jdbc.update(
        "INSERT INTO"
            + " whatsapp_message(id,conversation_id,direction,kind,body,request_key,status,created_at,updated_at,next_attempt_at,template_sid)"
            + " VALUES(?,?,'OUTBOUND',?,?,?,'QUEUED',?,?,?,?)",
        id,
        conversation,
        kind,
        body,
        key,
        Timestamp.from(now),
        Timestamp.from(now),
        Timestamp.from(now),
        templateSid);
    return message(id, false).orElseThrow();
  }

  public Optional<WhatsAppMessage> claim(Instant now) {
    jdbc.update(
        "UPDATE whatsapp_message SET status='UNKNOWN',error_message='El envío quedó sin"
            + " confirmación; revisa el proveedor antes de repetirlo.',updated_at=? WHERE"
            + " status='SENDING' AND updated_at<?",
        Timestamp.from(now),
        Timestamp.from(now.minusSeconds(60)));
    var pending =
        jdbc.query(
            "SELECT * FROM whatsapp_message WHERE status='QUEUED' AND next_attempt_at<=? ORDER BY"
                + " created_at,id LIMIT 1 FOR UPDATE SKIP LOCKED",
            MESSAGE,
            Timestamp.from(now));
    if (pending.isEmpty()) return Optional.empty();
    var m = pending.getFirst();
    jdbc.update(
        "UPDATE whatsapp_message SET status='SENDING',attempts=attempts+1,updated_at=? WHERE id=?",
        Timestamp.from(now),
        m.id());
    return message(m.id(), false);
  }

  public void updateDelivery(
      UUID id, String sid, String state, String code, String detail, Instant now) {
    jdbc.update(
        "UPDATE whatsapp_message SET"
            + " provider_sid=coalesce(provider_sid,?),status=?,error_code=?,error_message=?,updated_at=?"
            + " WHERE id=?",
        sid,
        state,
        code,
        detail,
        Timestamp.from(now),
        id);
  }

  public void retry(UUID id, Instant now) {
    jdbc.update(
        "UPDATE whatsapp_message SET"
            + " status='QUEUED',next_attempt_at=?,updated_at=?,error_code='20429',error_message='El"
            + " proveedor pidió esperar; reintento pendiente.' WHERE id=?",
        Timestamp.from(now.plusSeconds(10)),
        Timestamp.from(now),
        id);
  }

  public boolean event(UUID message, String sid, String status, String code, Instant now) {
    return jdbc.update(
            "INSERT INTO"
                + " whatsapp_delivery_event(id,message_id,provider_sid,status,error_code,received_at)"
                + " VALUES(?,?,?,?,?,?) ON CONFLICT(message_id,provider_sid,status,error_code) DO"
                + " NOTHING",
            UUID.randomUUID(),
            message,
            sid,
            status,
            code == null ? "" : code,
            Timestamp.from(now))
        == 1;
  }
}
