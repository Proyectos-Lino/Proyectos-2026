package com.odontocare.kapso.service;

import com.odontocare.agent.config.AgentProperties;
import com.odontocare.agent.service.AgentQueueService;
import com.odontocare.audit.service.AuditService;
import com.odontocare.kapso.config.KapsoProperties;
import com.odontocare.kapso.repository.KapsoRepository;
import com.odontocare.shared.pagination.*;
import com.odontocare.shared.web.ApiException;
import com.odontocare.whatsapp.dto.WhatsAppContracts.*;
import com.odontocare.whatsapp.model.*;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class KapsoMessagingService {
  private final KapsoProperties config;
  private final KapsoRepository repository;
  private final AuditService audit;
  private final AgentQueueService agent;
  private final AgentProperties ai;
  private final com.odontocare.agent.repository.AgentSupervisionRepository supervision;

  public KapsoMessagingService(
      KapsoProperties config,
      KapsoRepository repository,
      AuditService audit,
      AgentQueueService agent,
      AgentProperties ai,
      com.odontocare.agent.repository.AgentSupervisionRepository supervision) {
    this.config = config;
    this.repository = repository;
    this.audit = audit;
    this.agent = agent;
    this.ai = ai;
    this.supervision = supervision;
  }

  public Connection connection() {
    return new Connection(
        config.isEnabled(),
        config.ready(),
        "KAPSO_SANDBOX",
        config.getSender(),
        config.webhookUrl(),
        config.webhookUrl(),
        config.getAllowedParticipants().size(),
        "TEXT",
        false,
        config.missing(),
        config.isAgentEnabled() && ai.isEnabled());
  }

  @Transactional(readOnly = true)
  public PageResponse<WhatsAppConversation> list(PageQuery query) {
    return repository.conversations(config.getPhoneNumberId(), query);
  }

  @Transactional(readOnly = true)
  public WhatsAppConversation get(UUID id) {
    var conversation = repository.conversation(id, false).orElseThrow(ApiException::notFound);
    if (!repository.belongsTo(id, config.getPhoneNumberId())) throw ApiException.notFound();
    return conversation;
  }

  @Transactional(readOnly = true)
  public PageResponse<Message> messages(UUID id, PageQuery query, String direction, String state) {
    get(id);
    if (direction != null
        && !direction.isBlank()
        && !Set.of("INBOUND", "OUTBOUND").contains(direction))
      throw ApiException.badRequest("Dirección de mensaje inválida.");
    if (state != null
        && !state.isBlank()
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
            .contains(state)) throw ApiException.badRequest("Estado de mensaje inválido.");
    var page = repository.messages(id, query, direction, state);
    return new PageResponse<>(
        page.items().stream().map(Message::of).toList(),
        page.page(),
        page.size(),
        page.totalElements(),
        page.totalPages());
  }

  @Transactional
  public Message enqueue(UUID conversationId, String text, UUID key) {
    repository.keyLock(key);
    String body = text.strip();
    if (body.isBlank() || body.length() > 1600)
      throw ApiException.badRequest("Escribe un mensaje entre 1 y 1600 caracteres.");
    get(conversationId);
    var previous = repository.byKey(key);
    if (previous.isPresent()) {
      var old = previous.get();
      if (!old.conversationId().equals(conversationId) || !old.body().equals(body))
        throw ApiException.conflict("La referencia de envío ya pertenece a otro mensaje.");
      return Message.of(old);
    }
    var conversation = repository.conversation(conversationId, true).orElseThrow();
    if (config.isAgentEnabled() && ai.isEnabled()) {
      supervision.initialize(conversationId, "KAPSO");
      if (!"HUMAN".equals(supervision.control(conversationId, true).get("mode")))
        throw ApiException.conflict("Asume la conversación antes de enviar una respuesta manual.");
    }
    requireReplyAllowed(conversation);
    var now = Instant.now();
    var message = repository.enqueue(conversationId, body, key, now);
    repository.touch(conversationId, body, now, false);
    audit.record(
        "KAPSO_REPLY_QUEUED",
        "KAPSO_MESSAGE",
        message.id(),
        "Guardó texto manual pendiente de envío.");
    return Message.of(message);
  }

  private void requireReplyAllowed(WhatsAppConversation conversation) {
    if (!config.ready()
        || !config.permits(conversation.phone())
        || !repository.belongsTo(conversation.id(), config.getPhoneNumberId()))
      throw ApiException.badRequest("Completa la conexión de Kapso y autoriza al participante.");
    if (conversation.lastInboundAt() == null
        || !conversation.lastInboundAt().isAfter(Instant.now().minusSeconds(86400)))
      throw ApiException.badRequest(
          "Pide al participante un mensaje nuevo: la respuesta necesita la ventana de 24 horas.");
  }

  @Transactional
  public Optional<WhatsAppMessage> claim() {
    return repository.claim(config.getPhoneNumberId(), Instant.now());
  }

  @Transactional
  public String destination(WhatsAppMessage message) {
    var conversation = repository.conversation(message.conversationId(), false).orElseThrow();
    try {
      if (message.source().equals("AGENT") && !supervision.replyAllowed(message.id()))
        throw ApiException.conflict(
            "Recepción asumió el control; el envío automático fue suprimido.");
      requireReplyAllowed(conversation);
    } catch (ApiException failure) {
      finish(
          message.id(),
          new KapsoSender.Outcome(null, "FAILED", "LOCAL_POLICY", failure.getMessage()));
      return null;
    }
    return conversation.phone();
  }

  @Transactional
  public void finish(UUID id, KapsoSender.Outcome result) {
    if (result.reference() != null) repository.referenceLock(result.reference());
    var message = repository.message(id, true).orElseThrow();
    if (result.status().equals("RATE_LIMIT")
        && message.status().equals("SENDING")
        && message.attempts() < 3) {
      repository.retry(id, Instant.now());
      return;
    }
    if (result.reference() != null
        && message.providerSid() != null
        && !result.reference().equals(message.providerSid()))
      throw ApiException.conflict("La referencia de Kapso no corresponde al mensaje.");
    String next = result.status().equals("RATE_LIMIT") ? "FAILED" : result.status();
    apply(message, result.reference(), next, result.errorCode(), result.errorMessage());
    if (result.reference() != null) {
      var conversation = repository.conversation(message.conversationId(), false).orElseThrow();
      for (var receipt :
          repository.deliveryReceipts(
              config.getPhoneNumberId(), result.reference(), conversation.phone())) {
        var current = repository.message(id, true).orElseThrow();
        String code = receipt.get("error_code").toString();
        apply(
            current,
            result.reference(),
            receipt.get("state").toString(),
            code.isBlank() ? null : code,
            "FAILED".equals(receipt.get("state")) ? "Kapso informó un fallo de entrega." : null);
      }
    }
    audit.recordAs(
        null,
        "Kapso",
        "KAPSO_SEND_RESULT",
        "KAPSO_MESSAGE",
        id,
        "Registró resultado de envío: " + next);
  }

  public record Received(
      String reference, String phone, String name, String body, String kind, Instant timestamp) {}

  @Transactional
  public void received(Received incoming) {
    UUID conversation =
        repository.inboundConversation(
            config.getPhoneNumberId(), incoming.phone(), incoming.name(), incoming.timestamp());
    UUID id = UUID.randomUUID();
    if (repository.inbound(
        id,
        conversation,
        incoming.reference(),
        incoming.body(),
        incoming.kind(),
        incoming.timestamp())) {
      repository.touch(
          conversation,
          incoming.kind().equals("TEXT") ? incoming.body() : "Contenido no admitido en esta prueba",
          incoming.timestamp(),
          true);
      if (config.isAgentEnabled() && incoming.kind().equals("TEXT"))
        agent.inbound("KAPSO", id, conversation);
      audit.recordAs(
          null,
          "Kapso",
          "KAPSO_MESSAGE_RECEIVED",
          "KAPSO_MESSAGE",
          id,
          "Guardó un mensaje del participante; conexión manual sin agente.");
    }
  }

  @Transactional
  public void delivery(String reference, String phone, String state, String code) {
    var found = repository.bySid(reference);
    if (found.isEmpty())
      return; // The verified receipt is preserved even if the HTTP response is in flight.
    var message = found.get();
    var conversation = repository.conversation(message.conversationId(), false).orElseThrow();
    if (!message.direction().equals("OUTBOUND")
        || !conversation.phone().equals(phone)
        || !repository.belongsTo(conversation.id(), config.getPhoneNumberId()))
      throw ApiException.forbidden();
    apply(
        message,
        reference,
        state,
        code,
        state.equals("FAILED") ? "Kapso informó un fallo de entrega." : null);
    audit.recordAs(
        null,
        "Kapso",
        "KAPSO_DELIVERY_STATUS",
        "KAPSO_MESSAGE",
        message.id(),
        "Registró estado de entrega: " + state);
  }

  private void apply(
      WhatsAppMessage message, String reference, String state, String code, String detail) {
    String next = advance(message.status(), state);
    repository.updateDelivery(
        message.id(),
        reference,
        next,
        next.equals(state) ? code : message.errorCode(),
        next.equals(state) ? detail : message.errorMessage(),
        Instant.now());
  }

  private String advance(String current, String next) {
    if (current.equals("READ") || current.equals("DELIVERED") && !next.equals("READ"))
      return current;
    if (current.equals("FAILED") && !Set.of("DELIVERED", "READ").contains(next)) return current;
    var ranks =
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
    return ranks.getOrDefault(next, 0) >= ranks.getOrDefault(current, 0) ? next : current;
  }
}
