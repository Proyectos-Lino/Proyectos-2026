package com.odontocare.whatsapp.service;

import com.odontocare.whatsapp.config.WhatsAppProperties;
import com.odontocare.whatsapp.model.WhatsAppMessage;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Component
public class TwilioSender {
  public record Outcome(String sid, String status, String errorCode, String errorMessage) {}

  private final WhatsAppProperties config;
  private final ObjectMapper mapper;
  private final HttpClient client =
      HttpClient.newBuilder()
          .connectTimeout(Duration.ofSeconds(4))
          .followRedirects(HttpClient.Redirect.NEVER)
          .build();

  public TwilioSender(WhatsAppProperties config, ObjectMapper mapper) {
    this.config = config;
    this.mapper = mapper;
  }

  public Outcome send(WhatsAppMessage message, String phone) {
    var params = new LinkedHashMap<String, String>();
    params.put("From", config.getSender());
    params.put("To", "whatsapp:" + phone);
    params.put(
        "StatusCallback",
        config.url("/api/v1/integrations/whatsapp/status") + "?messageId=" + message.id());
    params.put(
        message.kind().equals("TEMPLATE") ? "ContentSid" : "Body",
        message.kind().equals("TEMPLATE") ? message.templateSid() : message.body());
    String form =
        params.entrySet().stream()
            .map(e -> encode(e.getKey()) + "=" + encode(e.getValue()))
            .reduce((a, b) -> a + "&" + b)
            .orElseThrow();
    String credential =
        Base64.getEncoder()
            .encodeToString(
                (config.getAccountSid() + ":" + config.getAuthToken())
                    .getBytes(StandardCharsets.UTF_8));
    var request =
        HttpRequest.newBuilder(
                URI.create(
                    "https://api.twilio.com/2010-04-01/Accounts/"
                        + config.getAccountSid()
                        + "/Messages.json"))
            .timeout(Duration.ofSeconds(8))
            .header("Authorization", "Basic " + credential)
            .header("Content-Type", "application/x-www-form-urlencoded")
            .POST(HttpRequest.BodyPublishers.ofString(form))
            .build();
    try {
      var response = client.send(request, HttpResponse.BodyHandlers.ofString());
      if (response.statusCode() == 429)
        return new Outcome(null, "RATE_LIMIT", "20429", "El proveedor pidió esperar.");
      if (response.statusCode() >= 500) return unknown();
      var json = mapper.readTree(response.body());
      if (response.statusCode() < 200 || response.statusCode() >= 300) {
        String code = json.path("code").asString("");
        if (!code.matches("[0-9]{1,10}")) code = "PROVIDER_REJECTED";
        return new Outcome(
            null,
            "FAILED",
            code,
            "Twilio rechazó el envío. Revisa el código en la guía de conexión.");
      }
      String sid = json.path("sid").asString("");
      if (!sid.matches("(SM|MM)[0-9a-fA-F]{32}")) return unknown();
      return new Outcome(sid, normalize(json.path("status").asString("queued")), null, null);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      return unknown();
    } catch (Exception e) {
      return unknown();
    }
  }

  private static String encode(String v) {
    return URLEncoder.encode(v, StandardCharsets.UTF_8);
  }

  private static Outcome unknown() {
    return new Outcome(
        null,
        "UNKNOWN",
        null,
        "No se pudo confirmar el envío. Revisa Twilio antes de repetirlo para evitar mensajes"
            + " duplicados.");
  }

  public static String normalize(String status) {
    return switch (status) {
      case "queued", "accepted", "sending" -> "ACCEPTED";
      case "sent" -> "SENT";
      case "delivered" -> "DELIVERED";
      case "read" -> "READ";
      case "failed", "undelivered", "canceled" -> "FAILED";
      default -> "UNKNOWN";
    };
  }
}
