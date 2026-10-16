package com.odontocare.kapso.config;

import java.net.URI;
import java.util.*;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties("odontocare.kapso")
public class KapsoProperties {
  private boolean enabled, workerEnabled = true;
  private boolean agentEnabled;

  public boolean isAgentEnabled() {
    return agentEnabled;
  }

  public void setAgentEnabled(boolean value) {
    agentEnabled = value;
  }

  private String apiKey = "",
      phoneNumberId = "",
      webhookSecret = "",
      sender = "",
      publicBaseUrl = "";
  private List<String> allowedParticipants = new ArrayList<>();

  public boolean isEnabled() {
    return enabled;
  }

  public void setEnabled(boolean value) {
    enabled = value;
  }

  public boolean isWorkerEnabled() {
    return workerEnabled;
  }

  public void setWorkerEnabled(boolean value) {
    workerEnabled = value;
  }

  public String getApiKey() {
    return apiKey;
  }

  public void setApiKey(String value) {
    apiKey = value.strip();
  }

  public String getPhoneNumberId() {
    return phoneNumberId;
  }

  public void setPhoneNumberId(String value) {
    phoneNumberId = value.strip();
  }

  public String getWebhookSecret() {
    return webhookSecret;
  }

  public void setWebhookSecret(String value) {
    webhookSecret = value.strip();
  }

  public String getSender() {
    return sender;
  }

  public void setSender(String value) {
    sender = value.strip();
  }

  public String getPublicBaseUrl() {
    return publicBaseUrl;
  }

  public void setPublicBaseUrl(String value) {
    publicBaseUrl = value.strip().replaceAll("/+$", "");
  }

  public List<String> getAllowedParticipants() {
    return List.copyOf(allowedParticipants);
  }

  public void setAllowedParticipants(List<String> value) {
    allowedParticipants = new ArrayList<>(value);
  }

  public boolean permits(String phone) {
    return allowedParticipants.contains(phone);
  }

  public String webhookUrl() {
    return publicBaseUrl + "/api/v1/integrations/kapso/events";
  }

  public boolean ready() {
    return enabled && missing().isEmpty();
  }

  public List<String> missing() {
    var missing = new ArrayList<String>();
    if (apiKey.isBlank() || apiKey.startsWith("REEMPLAZAR") || apiKey.length() > 512)
      missing.add("API key privada de Kapso");
    if (!phoneNumberId.matches("[0-9]{5,30}")) missing.add("Phone number ID del Sandbox");
    if (webhookSecret.length() < 16
        || webhookSecret.length() > 512
        || webhookSecret.startsWith("REEMPLAZAR")) missing.add("Secreto del webhook");
    if (!sender.matches("\\+[1-9][0-9]{7,14}")) missing.add("Número visible del Sandbox");
    try {
      var url = URI.create(publicBaseUrl);
      if (publicBaseUrl.contains("REEMPLAZAR")
          || !"https".equals(url.getScheme())
          || url.getHost() == null
          || url.getRawQuery() != null
          || url.getFragment() != null
          || url.getUserInfo() != null
          || !Set.of("", "/").contains(url.getPath())) missing.add("Dirección HTTPS pública");
    } catch (IllegalArgumentException failure) {
      missing.add("Dirección HTTPS pública");
    }
    if (allowedParticipants.isEmpty()
        || allowedParticipants.stream().anyMatch(p -> !p.matches("\\+[1-9][0-9]{7,14}")))
      missing.add("Participantes autorizados");
    return List.copyOf(missing);
  }
}
