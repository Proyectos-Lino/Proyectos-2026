package com.odontocare.agent.repository;

import com.odontocare.kapso.repository.KapsoRepository;
import com.odontocare.whatsapp.model.*;
import com.odontocare.whatsapp.repository.WhatsAppRepository;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class AgentInboxRepository {
  private final JdbcTemplate jdbc;
  private final WhatsAppRepository twilio;
  private final KapsoRepository kapso;

  public AgentInboxRepository(JdbcTemplate jdbc, WhatsAppRepository twilio, KapsoRepository kapso) {
    this.jdbc = jdbc;
    this.twilio = twilio;
    this.kapso = kapso;
  }

  public void register(String provider, UUID conversation, UUID message) {
    if (!Set.of("TWILIO", "KAPSO").contains(provider))
      throw new IllegalArgumentException("Unsupported inbox provider");
    String column = provider.equals("KAPSO") ? "kapso_id" : "whatsapp_id";
    jdbc.update(
        "INSERT INTO agent_conversation_source(id,provider,"
            + column
            + ") VALUES(?,?,?) ON CONFLICT(id) DO NOTHING",
        conversation,
        provider,
        conversation);
    jdbc.update(
        "INSERT INTO agent_message_source(id,conversation_id,provider,"
            + column
            + ") VALUES(?,?,?,?) ON CONFLICT(id) DO NOTHING",
        message,
        conversation,
        provider,
        message);
    var source =
        jdbc.queryForMap(
            "SELECT conversation_id,provider FROM agent_message_source WHERE id=?", message);
    if (!source.get("conversation_id").equals(conversation)
        || !source.get("provider").equals(provider))
      throw new IllegalStateException("Message source mismatch");
  }

  public String provider(UUID conversation) {
    return jdbc.queryForObject(
        "SELECT provider FROM agent_conversation_source WHERE id=?", String.class, conversation);
  }

  public Optional<WhatsAppConversation> conversation(UUID id, boolean lock) {
    var providers =
        jdbc.queryForList(
            "SELECT provider FROM agent_conversation_source WHERE id=?", String.class, id);
    if (providers.isEmpty()) return Optional.empty();
    return providers.getFirst().equals("KAPSO")
        ? kapso.conversation(id, lock)
        : twilio.conversation(id, lock);
  }

  public Optional<WhatsAppMessage> message(UUID id, boolean lock) {
    var providers =
        jdbc.queryForList("SELECT provider FROM agent_message_source WHERE id=?", String.class, id);
    if (providers.isEmpty()) return Optional.empty();
    return providers.getFirst().equals("KAPSO")
        ? kapso.message(id, lock)
        : twilio.message(id, lock);
  }

  public Optional<WhatsAppMessage> reply(UUID run) {
    var ids =
        jdbc.queryForList(
            "SELECT reply_message_id FROM agent_run WHERE id=? AND reply_message_id IS NOT NULL",
            UUID.class,
            run);
    return ids.isEmpty() ? Optional.empty() : kapso.message(ids.getFirst(), false);
  }
}
