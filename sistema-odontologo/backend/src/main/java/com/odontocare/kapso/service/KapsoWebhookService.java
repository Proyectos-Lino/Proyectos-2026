package com.odontocare.kapso.service;

import com.odontocare.audit.service.AuditService;
import com.odontocare.kapso.config.KapsoProperties;
import com.odontocare.kapso.repository.KapsoRepository;
import com.odontocare.shared.web.ApiException;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.*;
import java.util.*;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.*;

@Service
public class KapsoWebhookService {
  private static final Map<String, String> EVENTS =
      Map.of(
          "whatsapp.message.received",
          "RECEIVED",
          "whatsapp.message.sent",
          "SENT",
          "whatsapp.message.delivered",
          "DELIVERED",
          "whatsapp.message.read",
          "READ",
          "whatsapp.message.failed",
          "FAILED");
  private final KapsoProperties config;
  private final KapsoRepository repository;
  private final KapsoMessagingService messages;
  private final ObjectMapper mapper;
  private final AuditService audit;

  public KapsoWebhookService(
      KapsoProperties config,
      KapsoRepository repository,
      KapsoMessagingService messages,
      ObjectMapper mapper,
      AuditService audit) {
    this.config = config;
    this.repository = repository;
    this.messages = messages;
    this.mapper = mapper;
    this.audit = audit;
  }

  @Transactional
  public void receive(byte[] raw, String signature, String event, String key, String version) {
    if (!config.ready())
      throw new ApiException(
          HttpStatus.SERVICE_UNAVAILABLE,
          "Completa la conexión privada de Kapso antes de recibir eventos.");
    if (raw.length > 65536)
      throw new ApiException(
          HttpStatus.PAYLOAD_TOO_LARGE, "El evento supera el límite de la prueba.");
    verify(raw, signature);
    if (version != null && !version.equals("v2"))
      throw ApiException.badRequest("Configura el webhook de Kapso en formato v2.");
    if (event == null || !event.matches("[a-z._]{1,80}"))
      throw ApiException.badRequest("Falta el tipo de evento de Kapso.");
    if (!EVENTS.containsKey(event)) return;
    JsonNode body;
    try {
      body = mapper.readTree(raw);
    } catch (Exception failure) {
      throw ApiException.badRequest("El evento de Kapso debe contener JSON válido.");
    }
    if (!body.isObject()) throw ApiException.badRequest("El evento debe ser un objeto.");
    String hash = hash(raw);
    String deliveryKey = key == null || key.isBlank() ? hash : key;
    if (deliveryKey.length() > 140 || !deliveryKey.matches("[A-Za-z0-9_.:/=-]+"))
      throw ApiException.badRequest("Referencia de entrega inválida.");
    if (body.path("batch").asBoolean(false)) {
      var items = body.path("data");
      if (!items.isArray()
          || items.isEmpty()
          || items.size() > 50
          || !body.path("type").asString("").equals(event))
        throw ApiException.badRequest("El lote de mensajes no es válido.");
      int index = 0;
      for (var item : items) process(item, event, deliveryKey + "/" + index++, hash);
    } else process(body, event, deliveryKey, hash);
  }

  private void process(JsonNode payload, String event, String key, String hash) {
    if (!payload.isObject()
        || !payload.path("phone_number_id").asString("").equals(config.getPhoneNumberId()))
      throw ApiException.forbidden();
    var conversation = payload.path("conversation");
    var message = payload.path("message");
    var kapso = message.path("kapso");
    if (conversation.hasNonNull("phone_number_id")
        && !conversation.path("phone_number_id").asString().equals(config.getPhoneNumberId()))
      throw ApiException.forbidden();
    String reference = message.path("id").asString("");
    if (!KapsoSender.validReference(reference))
      throw ApiException.badRequest("Referencia de WhatsApp inválida.");
    String state = EVENTS.get(event);
    boolean incoming = state.equals("RECEIVED");
    if (!kapso.path("direction").asString("").equals(incoming ? "inbound" : "outbound")
        || (!incoming && !kapso.path("status").asString("").equals(state.toLowerCase(Locale.ROOT))))
      throw ApiException.badRequest("El tipo de evento no coincide con el mensaje.");
    String rawPhone = message.path(incoming ? "from" : "to").asString("");
    String contactPhone = conversation.path("phone_number").asString("");
    String phone = normalizePhone(rawPhone.isBlank() ? contactPhone : rawPhone);
    if (!contactPhone.isBlank() && !normalizePhone(contactPhone).equals(phone))
      throw ApiException.forbidden();
    if (phone.isBlank()) {
      if (repository.webhook(
          key,
          hash,
          event,
          config.getPhoneNumberId(),
          reference,
          "",
          "IGNORED",
          null,
          Instant.now()))
        audit.recordAs(
            null,
            "Kapso",
            "KAPSO_UNRESOLVED_CONTACT",
            "KAPSO_EVENT",
            null,
            "Omitió evento sin teléfono verificable; no vinculó pacientes.");
      return;
    }
    if (!config.permits(phone)) throw ApiException.forbidden();
    Instant timestamp;
    try {
      timestamp = Instant.ofEpochSecond(Long.parseLong(message.path("timestamp").asString("")));
    } catch (Exception failure) {
      throw ApiException.badRequest("La fecha del mensaje de Kapso no es válida.");
    }
    if (timestamp.isAfter(Instant.now().plusSeconds(120)))
      throw ApiException.badRequest("La fecha del mensaje está en el futuro.");
    String error = state.equals("FAILED") ? errorCode(kapso.path("statuses"), reference) : null;
    repository.referenceLock(reference);
    if (!repository.webhook(
        key, hash, event, config.getPhoneNumberId(), reference, phone, state, error, Instant.now()))
      return;
    if (incoming) {
      boolean text = message.path("type").asString("").equals("text");
      String content = text ? message.path("text").path("body").asString("") : "";
      if (content.length() > 4096)
        throw ApiException.badRequest("El mensaje excede 4096 caracteres.");
      String name = conversation.path("contact_name").asString("");
      name =
          name.substring(
              0, name.offsetByCodePoints(0, Math.min(120, name.codePointCount(0, name.length()))));
      messages.received(
          new KapsoMessagingService.Received(
              reference,
              phone,
              name,
              content,
              text && !content.isBlank() ? "TEXT" : "UNSUPPORTED",
              timestamp));
    } else messages.delivery(reference, phone, state, error);
  }

  private String normalizePhone(String value) {
    if (value.isBlank()) return "";
    String phone = value.startsWith("+") ? value : "+" + value;
    if (!phone.matches("\\+[1-9][0-9]{7,14}"))
      throw ApiException.badRequest("Teléfono de Kapso inválido.");
    return phone;
  }

  private String errorCode(JsonNode statuses, String reference) {
    String code = null;
    if (statuses.isArray())
      for (var status : statuses) {
        if (status.path("id").asString("").equals(reference)) {
          String candidate = status.path("errors").path(0).path("code").asString("");
          if (candidate.matches("[A-Za-z0-9_]{1,80}")) code = candidate;
        }
      }
    return code;
  }

  private void verify(byte[] raw, String signature) {
    if (signature == null || !signature.matches("[0-9a-fA-F]{64}")) throw ApiException.forbidden();
    try {
      var mac = Mac.getInstance("HmacSHA256");
      mac.init(
          new SecretKeySpec(
              config.getWebhookSecret().getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
      if (!MessageDigest.isEqual(mac.doFinal(raw), HexFormat.of().parseHex(signature)))
        throw ApiException.forbidden();
    } catch (GeneralSecurityException failure) {
      throw new IllegalStateException("Webhook verification unavailable");
    }
  }

  private String hash(byte[] raw) {
    try {
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(raw));
    } catch (NoSuchAlgorithmException failure) {
      throw new IllegalStateException(failure);
    }
  }
}
