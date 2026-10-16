package com.odontocare.kapso.service;

import com.odontocare.kapso.config.KapsoProperties;
import com.odontocare.whatsapp.model.WhatsAppMessage;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Component
public class KapsoSender {
  public record Outcome(String reference, String status, String errorCode, String errorMessage) {}

  private final KapsoProperties config;
  private final ObjectMapper mapper;
  private final URI baseUrl;
  private final HttpClient client =
      HttpClient.newBuilder()
          .connectTimeout(Duration.ofSeconds(4))
          .followRedirects(HttpClient.Redirect.NEVER)
          .build();

  @Autowired
  public KapsoSender(KapsoProperties config, ObjectMapper mapper) {
    this(config, mapper, URI.create("https://api.kapso.ai/meta/whatsapp/v24.0/"));
  }

  KapsoSender(KapsoProperties config, ObjectMapper mapper, URI baseUrl) {
    this.config = config;
    this.mapper = mapper;
    this.baseUrl = baseUrl;
  }

  public Outcome send(WhatsAppMessage message, String phone) {
    if (!config.ready() || !message.kind().equals("TEXT") || !config.permits(phone))
      return new Outcome(
          null, "FAILED", "LOCAL_POLICY", "La conexión o el participante no están habilitados.");
    var body =
        Map.of(
            "messaging_product",
            "whatsapp",
            "to",
            phone.substring(1),
            "type",
            "text",
            "text",
            Map.of("body", message.body()));
    var request =
        HttpRequest.newBuilder(baseUrl.resolve(config.getPhoneNumberId() + "/messages"))
            .timeout(Duration.ofSeconds(8))
            .header("X-API-Key", config.getApiKey())
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body)))
            .build();
    try {
      var response = client.send(request, HttpResponse.BodyHandlers.ofString());
      if (response.statusCode() == 429)
        return new Outcome(null, "RATE_LIMIT", "RATE_LIMIT", "Kapso pidió esperar.");
      if (response.statusCode() >= 500) return unknown();
      var json = mapper.readTree(response.body());
      if (response.statusCode() < 200 || response.statusCode() >= 300) {
        String code =
            json.path("error")
                .path("code")
                .asString(json.path("code").asString("PROVIDER_REJECTED"));
        if (!code.matches("[A-Za-z0-9_]{1,80}")) code = "PROVIDER_REJECTED";
        String detail =
            switch (response.statusCode()) {
              case 401, 403 ->
                  "Kapso rechazó la clave o los permisos. Revisa la configuración privada.";
              case 402 -> "Kapso requiere saldo o resolver la facturación. Revisa Billing y Usage.";
              default ->
                  "Kapso rechazó el envío. Revisa el código, la sesión del Sandbox y la ventana de"
                      + " 24 horas.";
            };
        if (response.statusCode() == 402
            && (code.equals("insufficient_credits") || code.equals("service_credits_exhausted")))
          detail =
              "Saldo insuficiente en Kapso para enviar el mensaje. Revisa los créditos del proyecto"
                  + " en Billing; el cupo de mensajes del plan es independiente del saldo de"
                  + " envío.";
        return new Outcome(null, "FAILED", code, detail);
      }
      String id = json.path("messages").path(0).path("id").asString("");
      if (!validReference(id)) return unknown();
      return new Outcome(id, "ACCEPTED", null, null);
    } catch (InterruptedException failure) {
      Thread.currentThread().interrupt();
      return unknown();
    } catch (Exception failure) {
      return unknown();
    }
  }

  public static boolean validReference(String reference) {
    return reference.matches("wamid\\.[A-Za-z0-9+/_=.-]{1,506}");
  }

  private Outcome unknown() {
    return new Outcome(
        null,
        "UNKNOWN",
        null,
        "No se pudo confirmar el envío. Revisa Kapso antes de repetirlo para evitar mensajes"
            + " duplicados.");
  }
}
