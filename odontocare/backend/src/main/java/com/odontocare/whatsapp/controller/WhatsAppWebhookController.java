package com.odontocare.whatsapp.controller;

import com.odontocare.shared.web.ApiException;
import com.odontocare.whatsapp.config.WhatsAppProperties;
import com.odontocare.whatsapp.service.*;
import jakarta.servlet.http.HttpServletRequest;
import java.util.UUID;
import org.springframework.http.*;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/integrations/whatsapp")
public class WhatsAppWebhookController {
  private final WhatsAppSignatureService signatures;
  private final WhatsAppConversationService conversations;
  private final WhatsAppOutboxService outbox;
  private final WhatsAppProperties config;

  public WhatsAppWebhookController(
      WhatsAppSignatureService signatures,
      WhatsAppConversationService conversations,
      WhatsAppOutboxService outbox,
      WhatsAppProperties config) {
    this.signatures = signatures;
    this.conversations = conversations;
    this.outbox = outbox;
    this.config = config;
  }

  @PostMapping(value = "/inbound", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
  public ResponseEntity<String> inbound(
      @RequestHeader(value = "X-Twilio-Signature", required = false) String signature,
      @RequestBody MultiValueMap<String, String> form,
      HttpServletRequest request) {
    if (request.getQueryString() != null)
      throw ApiException.badRequest("La recepción no admite parámetros en la dirección.");
    conversations.receive(
        signatures.validate("/api/v1/integrations/whatsapp/inbound", null, signature, form));
    // The new trial does not support direct TwiML; classic text senders expect empty TwiML.
    return config.getSendMode().equals("TEXT")
        ? ResponseEntity.ok().contentType(MediaType.APPLICATION_XML).body("<Response></Response>")
        : ResponseEntity.ok().contentType(MediaType.TEXT_PLAIN).body("");
  }

  @PostMapping(value = "/status", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
  public ResponseEntity<Void> status(
      @RequestHeader(value = "X-Twilio-Signature", required = false) String signature,
      @RequestBody MultiValueMap<String, String> form,
      HttpServletRequest request,
      @RequestParam(required = false) UUID messageId) {
    outbox.status(
        signatures.validate(
            "/api/v1/integrations/whatsapp/status", request.getQueryString(), signature, form),
        messageId);
    return ResponseEntity.ok().build();
  }
}
