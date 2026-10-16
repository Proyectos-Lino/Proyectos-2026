package com.odontocare.agent.repository;

import com.odontocare.shared.pagination.*;
import java.util.*;
import org.springframework.data.domain.PageImpl;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class AgentInboxQueryRepository {
  private final JdbcTemplate jdbc;

  public AgentInboxQueryRepository(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public PageResponse<Map<String, Object>> list(
      String number, PageQuery query, String mode, String state) {
    var page =
        query.pageable(
            Map.of("name", "contact_name", "lastMessageAt", "last_message_at", "phone", "phone"));
    var args = new ArrayList<Object>();
    args.add(number);
    String where = " WHERE k.phone_number_id=?";
    if (!mode.isBlank()) {
      where += " AND coalesce(s.mode,'AUTO')=?";
      args.add(mode);
    }
    if (!state.isBlank()) {
      where += " AND coalesce(c.state,'INFORMATION_PENDING')=?";
      args.add(state);
    }
    String search =
        "%"
            + query
                .getSearch()
                .toLowerCase(Locale.ROOT)
                .replace("!", "!!")
                .replace("%", "!%")
                .replace("_", "!_")
            + "%";
    where +=
        " AND (lower(k.phone) LIKE ? ESCAPE '!' OR lower(k.contact_name) LIKE ? ESCAPE '!' OR"
            + " lower(k.last_message_preview) LIKE ? ESCAPE '!' OR lower(coalesce(c.summary,''))"
            + " LIKE ? ESCAPE '!' OR lower(coalesce(c.patient_name,'')) LIKE ? ESCAPE '!')";
    for (int n = 0; n < 5; n++) args.add(search);
    String join =
        " FROM kapso_conversation k LEFT JOIN agent_supervision s ON s.conversation_id=k.id LEFT"
            + " JOIN agent_request_context c ON c.conversation_id=k.id AND c.source='KAPSO'";
    long count = jdbc.queryForObject("SELECT count(*)" + join + where, Long.class, args.toArray());
    args.add(page.getPageSize());
    args.add(page.getOffset());
    String order =
        page.getSort().stream()
            .map(o -> "k." + o.getProperty() + " " + o.getDirection())
            .reduce((a, b) -> a + "," + b)
            .orElseThrow();
    var rows =
        jdbc.query(
            "SELECT k.*,coalesce(s.mode,'AUTO') mode,coalesce(c.state,'INFORMATION_PENDING')"
                + " request_state,c.summary,c.patient_name,c.appointment_id"
                + join
                + where
                + " ORDER BY "
                + order
                + ",k.id LIMIT ? OFFSET ?",
            (r, i) -> {
              var item = new LinkedHashMap<String, Object>();
              item.put("id", r.getObject("id", UUID.class));
              item.put("phone", r.getString("phone"));
              item.put("contactName", r.getString("contact_name"));
              item.put("lastMessageAt", r.getTimestamp("last_message_at").toInstant());
              item.put("lastMessagePreview", r.getString("last_message_preview"));
              item.put("mode", r.getString("mode"));
              item.put("requestState", r.getString("request_state"));
              item.put("summary", r.getString("summary"));
              item.put("patientName", r.getString("patient_name"));
              item.put("appointmentId", r.getObject("appointment_id", UUID.class));
              return (Map<String, Object>) item;
            },
            args.toArray());
    return PageResponse.of(new PageImpl<>(rows, page, count));
  }
}
