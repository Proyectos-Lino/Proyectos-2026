package com.odontocare.whatsapp.service;

import com.odontocare.agent.config.AgentProperties;
import com.odontocare.agent.service.AgentQueueService;
import com.odontocare.audit.service.AuditService;
import com.odontocare.kapso.config.KapsoProperties;
import com.odontocare.kapso.service.KapsoMessagingService;
import com.odontocare.shared.pagination.*;
import com.odontocare.shared.web.ApiException;
import com.odontocare.whatsapp.config.WhatsAppProperties;
import com.odontocare.whatsapp.dto.WhatsAppContracts.*;
import com.odontocare.whatsapp.model.*;
import com.odontocare.whatsapp.repository.WhatsAppRepository;
import java.time.Instant;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WhatsAppConversationService {
  private final WhatsAppRepository repository;
  private final WhatsAppProperties config;
  private final AuditService audit;
  private final AgentQueueService agentQueue;
  private final AgentProperties agentConfig;
  private final KapsoProperties kapsoConfig;
  private final KapsoMessagingService kapso;

  public WhatsAppConversationService(
      WhatsAppRepository repository,
      WhatsAppProperties config,
      AuditService audit,
      AgentQueueService agentQueue,
      AgentProperties agentConfig,
      KapsoProperties kapsoConfig,
      KapsoMessagingService kapso) {
    this.repository = repository;
    this.config = config;
    this.audit = audit;
    this.agentQueue = agentQueue;
    this.agentConfig = agentConfig;
    this.kapsoConfig = kapsoConfig;
    this.kapso = kapso;
  }

  public Connection connection() {
    if (kapsoConfig.isEnabled()) return kapso.connection();
    return new Connection(
        config.isEnabled(),
        config.ready(),
        "TWILIO_SANDBOX",
        config.getSender(),
        config.url("/api/v1/integrations/whatsapp/inbound"),
        config.url("/api/v1/integrations/whatsapp/status"),
        config.getAllowedParticipants().size(),
        config.getSendMode(),
        config.templateReady(),
        config.missing(),
        agentConfig.isEnabled());
  }

  @Transactional(readOnly = true)
  public PageResponse<WhatsAppConversation> list(PageQuery query) {
    if (kapsoConfig.isEnabled()) return kapso.list(query);
    return repository.conversations(query);
  }

  @Transactional(readOnly = true)
  public WhatsAppConversation get(UUID id) {
    if (kapsoConfig.isEnabled()) return kapso.get(id);
    return repository.conversation(id, false).orElseThrow(ApiException::notFound);
  }

  @Transactional(readOnly = true)
  public PageResponse<Message> messages(UUID id, PageQuery query, String direction, String status) {
    if (kapsoConfig.isEnabled()) return kapso.messages(id, query, direction, status);
    get(id);
    if (direction != null
        && !direction.isBlank()
        && !Set.of("INBOUND", "OUTBOUND").contains(direction))
      throw ApiException.badRequest("Dirección de mensaje inválida.");
    if (status != null
        && !status.isBlank()
        && !Set.of(
                "QUEUED",
                "SENDING",
                "ACCEPTED",
                "SENT",
                "DELIVERED",
                "READ",
                "FAILED",
                "UNKNOWN",
                "RECEIVED",
                "UNSUPPORTED")
            .contains(status)) throw ApiException.badRequest("Estado de mensaje inválido.");
    var p = repository.messages(id, query, direction, status);
    return new PageResponse<>(
        p.items().stream().map(Message::of).toList(),
        p.page(),
        p.size(),
        p.totalElements(),
        p.totalPages());
  }

  @Transactional
  public Message enqueue(UUID id, String body, UUID key, boolean template) {
    if (kapsoConfig.isEnabled()) {
      if (template)
        throw ApiException.badRequest(
            "El Sandbox de Kapso admite texto personalizado, no plantillas.");
      return kapso.enqueue(id, body, key);
    }
    repository.keyLock(key);
    var prior = repository.byKey(key);
    String content = template ? "Plantilla de prueba de Twilio" : body.strip(),
        kind = template ? "TEMPLATE" : "TEXT";
    if (prior.isPresent()) {
      var old = prior.get();
      if (!old.conversationId().equals(id)
          || !old.body().equals(content)
          || !old.kind().equals(kind))
        throw ApiException.conflict("La referencia de envío ya se utilizó con otro mensaje.");
      return Message.of(old);
    }
    var conversation = repository.conversation(id, true).orElseThrow(ApiException::notFound);
    if (!config.ready() || !config.permits(conversation.phone()))
      throw ApiException.badRequest(
          "La conexión debe estar habilitada y el participante autorizado.");
    if (template
        ? !config.getSendMode().equals("TEMPLATE") || !config.templateReady()
        : !config.getSendMode().equals("TEXT"))
      throw ApiException.badRequest("Este tipo de respuesta no está habilitado en la conexión.");
    if (conversation.lastInboundAt() == null
        || conversation.lastInboundAt().isBefore(Instant.now().minusSeconds(86400)))
      throw ApiException.badRequest(
          "Pide al participante que envíe un mensaje nuevo antes de responder a esta prueba.");
    var now = Instant.now();
    var message =
        repository.enqueue(
            id, content, kind, key, now, template ? config.getTestTemplateSid() : null);
    repository.touch(id, content, now, false);
    audit.record(
        "WHATSAPP_REPLY_QUEUED",
        "WHATSAPP_MESSAGE",
        message.id(),
        "Registró una respuesta pendiente de envío.");
    return Message.of(message);
  }

  @Transactional
  public void receive(Map<String, String> form) {
    if (kapsoConfig.isEnabled()) throw ApiException.forbidden();
    if (!config.getSender().equals(form.get("To"))) throw ApiException.forbidden();
    String address = form.getOrDefault("From", "");
    if (!address.startsWith("whatsapp:")) throw ApiException.forbidden();
    String phone = address.substring(9);
    if (!config.permits(phone)) throw ApiException.forbidden();
    String sid = form.getOrDefault("MessageSid", "");
    if (!sid.matches("(SM|MM)[0-9a-fA-F]{32}"))
      throw ApiException.badRequest("Referencia de mensaje inválida.");
    String body = form.getOrDefault("Body", "");
    if (body.length() > 4096)
      throw ApiException.badRequest("El mensaje excede el tamaño admitido.");
    boolean text = form.getOrDefault("NumMedia", "0").equals("0") && !body.isBlank();
    String name = form.getOrDefault("ProfileName", "");
    name =
        name.substring(
            0, name.offsetByCodePoints(0, Math.min(120, name.codePointCount(0, name.length()))));
    var now = Instant.now();
    UUID conversation = repository.inboundConversation(phone, name, now), id = UUID.randomUUID();
    if (repository.inbound(id, conversation, sid, body, text ? "TEXT" : "UNSUPPORTED", now)) {
      if (text) agentQueue.inbound(id, conversation);
      repository.touch(
          conversation, text ? body : "Mensaje con contenido no compatible", now, true);
      audit.recordAs(
          null,
          "WhatsApp",
          "WHATSAPP_MESSAGE_RECEIVED",
          "WHATSAPP_MESSAGE",
          id,
          "Guardó un mensaje del participante autorizado.");
    }
  }
}
