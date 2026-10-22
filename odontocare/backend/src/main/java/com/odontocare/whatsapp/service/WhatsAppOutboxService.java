package com.odontocare.whatsapp.service;

import com.odontocare.audit.service.AuditService;
import com.odontocare.shared.web.ApiException;
import com.odontocare.whatsapp.config.WhatsAppProperties;
import com.odontocare.whatsapp.model.WhatsAppMessage;
import com.odontocare.whatsapp.repository.WhatsAppRepository;
import java.time.Instant;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WhatsAppOutboxService {
  private final WhatsAppRepository repository;
  private final WhatsAppProperties config;
  private final AuditService audit;

  public WhatsAppOutboxService(
      WhatsAppRepository repository, WhatsAppProperties config, AuditService audit) {
    this.repository = repository;
    this.config = config;
    this.audit = audit;
  }

  @Transactional
  public Optional<WhatsAppMessage> claim() {
    return repository.claim(Instant.now());
  }

  @Transactional
  public String destination(WhatsAppMessage m) {
    var conversation = repository.conversation(m.conversationId(), false).orElseThrow();
    if (!config.ready()
        || !config.permits(conversation.phone())
        || conversation.lastInboundAt() == null
        || conversation.lastInboundAt().isBefore(Instant.now().minusSeconds(86400))
        || !config.getSendMode().equals(m.kind())
        || (m.kind().equals("TEMPLATE") && !config.templateReady())) {
      finish(
          m.id(),
          new TwilioSender.Outcome(
              null,
              "FAILED",
              "LOCAL_POLICY",
              "El envío ya no cumple la configuración o el periodo de respuesta de prueba."));
      return null;
    }
    return conversation.phone();
  }

  @Transactional
  public void finish(UUID id, TwilioSender.Outcome result) {
    var m = repository.message(id, true).orElseThrow();
    if (result.status().equals("RATE_LIMIT") && m.status().equals("SENDING") && m.attempts() < 3) {
      repository.retry(id, Instant.now());
      return;
    }
    String next = result.status().equals("RATE_LIMIT") ? "FAILED" : result.status();
    if (result.sid() != null && m.providerSid() != null && !result.sid().equals(m.providerSid()))
      throw ApiException.conflict("La referencia del proveedor no corresponde al mensaje.");
    String applied = advance(m.status(), next);
    repository.updateDelivery(
        id,
        result.sid(),
        applied,
        applied.equals(next) ? result.errorCode() : m.errorCode(),
        applied.equals(next) ? result.errorMessage() : m.errorMessage(),
        Instant.now());
    audit.recordAs(
        null,
        "WhatsApp",
        "WHATSAPP_SEND_RESULT",
        "WHATSAPP_MESSAGE",
        id,
        "Registró el resultado del envío: " + next);
  }

  @Transactional
  public void status(Map<String, String> form, UUID messageId) {
    String sid = form.getOrDefault("MessageSid", "");
    if (!sid.matches("(SM|MM)[0-9a-fA-F]{32}"))
      throw ApiException.badRequest("Referencia de mensaje inválida.");
    var found = messageId == null ? repository.bySid(sid) : repository.message(messageId, true);
    if (found.isEmpty()) return;
    var m = found.get();
    if (!m.direction().equals("OUTBOUND")) return;
    if (m.providerSid() != null && !m.providerSid().equals(sid)) throw ApiException.forbidden();
    if (form.containsKey("From") && !config.getSender().equals(form.get("From")))
      throw ApiException.forbidden();
    if (form.containsKey("To")
        && !form.get("To")
            .equals(
                "whatsapp:"
                    + repository.conversation(m.conversationId(), false).orElseThrow().phone()))
      throw ApiException.forbidden();
    String next =
        TwilioSender.normalize(
            form.getOrDefault("MessageStatus", form.getOrDefault("SmsStatus", "")));
    if (next.equals("UNKNOWN")) return;
    String code = form.get("ErrorCode");
    if (code != null && !code.matches("[0-9]{1,10}")) code = null;
    if (!repository.event(m.id(), sid, next, code, Instant.now())) return;
    String applied = advance(m.status(), next);
    repository.updateDelivery(
        m.id(),
        sid,
        applied,
        applied.equals(next) ? code : m.errorCode(),
        applied.equals(next)
            ? (next.equals("FAILED") ? "El proveedor informó un fallo de entrega." : null)
            : m.errorMessage(),
        Instant.now());
    audit.recordAs(
        null,
        "WhatsApp",
        "WHATSAPP_DELIVERY_STATUS",
        "WHATSAPP_MESSAGE",
        m.id(),
        "Registró el estado de entrega: " + next);
  }

  private String advance(String current, String next) {
    if (current.equals("READ") || current.equals("DELIVERED") && !next.equals("READ"))
      return current;
    if (current.equals("FAILED") && !Set.of("DELIVERED", "READ").contains(next)) return current;
    var rank =
        Map.of(
            "QUEUED",
            0,
            "SENDING",
            1,
            "UNKNOWN",
            1,
            "ACCEPTED",
            2,
            "SENT",
            3,
            "DELIVERED",
            4,
            "READ",
            5,
            "FAILED",
            6);
    return rank.getOrDefault(next, 0) >= rank.getOrDefault(current, 0) ? next : current;
  }
}
