package com.odontocare.kapso.controller;

import com.odontocare.kapso.service.KapsoWebhookService;
import com.odontocare.shared.web.ApiException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/integrations/kapso")
public class KapsoWebhookController {
  private final KapsoWebhookService webhooks;

  public KapsoWebhookController(KapsoWebhookService webhooks) {
    this.webhooks = webhooks;
  }

  @PostMapping(value = "/events", consumes = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<Void> receive(
      @RequestBody byte[] raw,
      @RequestHeader(value = "X-Webhook-Signature", required = false) String signature,
      @RequestHeader(value = "X-Webhook-Event", required = false) String event,
      @RequestHeader(value = "X-Idempotency-Key", required = false) String key,
      @RequestHeader(value = "X-Webhook-Payload-Version", required = false) String version,
      HttpServletRequest request) {
    if (request.getQueryString() != null)
      throw ApiException.badRequest("El receptor de Kapso no admite parámetros en la dirección.");
    webhooks.receive(raw, signature, event, key, version);
    return ResponseEntity.ok().build();
  }
}
