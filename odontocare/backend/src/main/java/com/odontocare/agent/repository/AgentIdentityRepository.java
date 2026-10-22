package com.odontocare.agent.repository;

import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class AgentIdentityRepository {
  private final JdbcTemplate jdbc;

  public AgentIdentityRepository(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public List<String> recent(UUID conversation, String source, UUID message) {
    return jdbc.queryForList(
        "SELECT body FROM agent_inbox_message WHERE conversation_id=? AND source=? AND"
            + " direction='INBOUND' AND sequence_no<=(SELECT sequence_no FROM agent_inbox_message"
            + " WHERE id=?) ORDER BY sequence_no DESC LIMIT 6",
        String.class,
        conversation,
        source,
        message);
  }

  public boolean guardian(UUID patient, String phone) {
    return Boolean.TRUE.equals(
        jdbc.queryForObject(
            "SELECT EXISTS(SELECT 1 FROM patient_contact WHERE patient_id=? AND phone=? AND"
                + " guardian)",
            Boolean.class,
            patient,
            phone));
  }

  public boolean minor(UUID patient, java.time.LocalDate today) {
    return Boolean.TRUE.equals(
        jdbc.queryForObject(
            "SELECT birth_date>? FROM patient WHERE id=?",
            Boolean.class,
            today.minusYears(18),
            patient));
  }
}
