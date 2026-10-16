package com.odontocare.whatsapp.repository;

import com.odontocare.whatsapp.dto.ChatQuery;
import com.odontocare.whatsapp.dto.WhatsAppContracts.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class ChatTimelineRepository {
  private final JdbcTemplate jdbc;

  public ChatTimelineRepository(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public ChatPage messages(UUID conversation, boolean kapso, ChatQuery query) {
    // Table and provider are application choices, never supplied by the HTTP caller.
    String table = kapso ? "kapso_message" : "whatsapp_message";
    String provider = kapso ? "KAPSO" : "TWILIO";
    String where = " WHERE s.conversation_id=? AND s.provider=? AND m.source<>'APP_TEST'";
    var args = new ArrayList<Object>(List.of(conversation, provider));
    if (query.getBefore() != null) {
      where += " AND s.sequence_no<?";
      args.add(query.getBefore());
    }
    if (query.getAfter() != null) {
      where += " AND s.sequence_no>?";
      args.add(query.getAfter());
    }
    if (!query.getSearch().isBlank()) {
      where += " AND lower(m.body) LIKE ? ESCAPE '!'";
      args.add(
          "%"
              + query
                  .getSearch()
                  .toLowerCase(Locale.ROOT)
                  .replace("!", "!!")
                  .replace("%", "!%")
                  .replace("_", "!_")
              + "%");
    }
    if (!query.getMessageDirection().isBlank()) {
      where += " AND m.direction=?";
      args.add(query.getMessageDirection());
    }
    if (!query.getStatus().isBlank()) {
      where += " AND m.status=?";
      args.add(query.getStatus());
    }
    args.add(query.getSize() + 1);
    var rows =
        jdbc.query(
            "SELECT s.sequence_no,m.* FROM agent_message_source s JOIN "
                + table
                + " m ON m.id=s.id"
                + where
                + " ORDER BY s.sequence_no "
                + (query.getAfter() == null ? "DESC" : "ASC")
                + " LIMIT ?",
            (r, i) ->
                new ChatItem(
                    r.getLong("sequence_no"),
                    new Message(
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
                        r.getString("source"))),
            args.toArray());
    boolean more = rows.size() > query.getSize();
    if (more) rows.removeLast();
    if (query.getAfter() == null) Collections.reverse(rows);
    return new ChatPage(
        List.copyOf(rows),
        rows.isEmpty() ? null : rows.getFirst().sequence(),
        rows.isEmpty() ? null : rows.getLast().sequence(),
        more);
  }
}
