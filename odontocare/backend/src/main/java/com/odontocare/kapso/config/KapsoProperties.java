package com.odontocare.kapso.config;

import java.net.URI;
import java.util.*;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties("odontocare.kapso")
public class KapsoProperties {
  @org.springframework.beans.factory.annotation.Autowired(required = false)
  private com.odontocare.integrations.service.RuntimeIntegrationState integrations;

  public boolean managed() {
    return integrations != null && integrations.managed();
  }

  private boolean enabled, workerEnabled = true;
  private boolean agentEnabled;

  public boolean isAgentEnabled() {
    return managed()
        ? integrations.current().aiEnabled() && integrations.current().whatsappEnabled()
        : agentEnabled;
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
    return managed() ? integrations.current().whatsappEnabled() : enabled;
  }

  public void setEnabled(boolean value) {
    enabled = value;
  }

  public boolean isWorkerEnabled() {
    return workerEnabled && (!managed() || integrations.current().whatsappEnabled());
  }

  public void setWorkerEnabled(boolean value) {
    workerEnabled = value;
  }

  public String getApiKey() {
    return managed() ? integrations.current().kapsoKey() : apiKey;
  }

  public void setApiKey(String value) {
    apiKey = value.strip();
  }

  public String getPhoneNumberId() {
    return managed() ? integrations.current().phoneNumberId() : phoneNumberId;
  }

  public void setPhoneNumberId(String value) {
    phoneNumberId = value.strip();
  }

  public String getWebhookSecret() {
    return managed() ? integrations.current().webhookSecret() : webhookSecret;
  }

  public void setWebhookSecret(String value) {
    webhookSecret = value.strip();
  }

  public String getSender() {
    return managed() ? integrations.current().sender() : sender;
  }

  public void setSender(String value) {
    sender = value.strip();
  }

  public String getPublicBaseUrl() {
    return managed()
        ? (integrations.fixedPublicUrl().isBlank()
            ? integrations.current().publicBaseUrl()
            : integrations.fixedPublicUrl())
        : publicBaseUrl;
  }

  public void setPublicBaseUrl(String value) {
    publicBaseUrl = value.strip().replaceAll("/+$", "");
  }

  public List<String> getAllowedParticipants() {
    return managed() ? integrations.current().participants() : List.copyOf(allowedParticipants);
  }

  public void setAllowedParticipants(List<String> value) {
    allowedParticipants = new ArrayList<>(value);
  }

  public boolean permits(String phone) {
    return managed() && !integrations.current().restrictParticipants()
        || getAllowedParticipants().contains(phone);
  }

  public String webhookUrl() {
    return getPublicBaseUrl().isBlank()
        ? ""
        : getPublicBaseUrl() + "/api/v1/integrations/kapso/events";
  }

  public boolean ready() {
    return isEnabled() && missing().isEmpty();
  }

  public List<String> missing() {
    var missing = new ArrayList<String>();
    if (getApiKey().isBlank() || getApiKey().startsWith("REEMPLAZAR") || getApiKey().length() > 512)
      missing.add("API key privada de Kapso");
    if (!getPhoneNumberId().matches("[0-9]{5,30}")) missing.add("Phone number ID del Sandbox");
    if (getWebhookSecret().length() < 16
        || getWebhookSecret().length() > 512
        || getWebhookSecret().startsWith("REEMPLAZAR")) missing.add("Secreto del webhook");
    if (!getSender().matches("\\+[1-9][0-9]{7,14}")) missing.add("Número visible del Sandbox");
    try {
      var url = URI.create(getPublicBaseUrl());
      if (getPublicBaseUrl().contains("REEMPLAZAR")
          || !"https".equals(url.getScheme())
          || url.getHost() == null
          || url.getRawQuery() != null
          || url.getFragment() != null
          || url.getUserInfo() != null
          || !Set.of("", "/").contains(url.getPath())) missing.add("Dirección HTTPS pública");
    } catch (IllegalArgumentException failure) {
      missing.add("Dirección HTTPS pública");
    }
    if ((!(managed() && !integrations.current().restrictParticipants())
            && getAllowedParticipants().isEmpty())
        || getAllowedParticipants().stream().anyMatch(p -> !p.matches("\\+[1-9][0-9]{7,14}")))
      missing.add("Participantes autorizados");
    return List.copyOf(missing);
  }
}
