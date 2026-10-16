package com.odontocare.whatsapp.config;

import java.net.URI;
import java.util.*;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties("odontocare.whatsapp")
public class WhatsAppProperties {
  private boolean enabled, workerEnabled = true;
  private String accountSid = "",
      authToken = "",
      sender = "",
      publicBaseUrl = "",
      sendMode = "TEMPLATE",
      testTemplateSid = "";
  private List<String> allowedParticipants = new ArrayList<>();

  public boolean isEnabled() {
    return enabled;
  }

  public void setEnabled(boolean v) {
    enabled = v;
  }

  public boolean isWorkerEnabled() {
    return workerEnabled;
  }

  public void setWorkerEnabled(boolean v) {
    workerEnabled = v;
  }

  public String getAccountSid() {
    return accountSid;
  }

  public void setAccountSid(String v) {
    accountSid = v.strip();
  }

  public String getAuthToken() {
    return authToken;
  }

  public void setAuthToken(String v) {
    authToken = v.strip();
  }

  public String getSender() {
    return sender;
  }

  public void setSender(String v) {
    sender = v.strip();
  }

  public String getPublicBaseUrl() {
    return publicBaseUrl;
  }

  public void setPublicBaseUrl(String v) {
    publicBaseUrl = v.strip().replaceAll("/+$", "");
  }

  public String getSendMode() {
    return sendMode;
  }

  public void setSendMode(String v) {
    sendMode = v.strip().toUpperCase(Locale.ROOT);
  }

  public String getTestTemplateSid() {
    return testTemplateSid;
  }

  public void setTestTemplateSid(String v) {
    testTemplateSid = v.strip();
  }

  public List<String> getAllowedParticipants() {
    return List.copyOf(allowedParticipants);
  }

  public void setAllowedParticipants(List<String> v) {
    allowedParticipants = new ArrayList<>(v);
  }

  public boolean permits(String phone) {
    return allowedParticipants.contains(phone);
  }

  public String url(String path) {
    return publicBaseUrl + path;
  }

  public List<String> missing() {
    return missing(true);
  }

  private List<String> missing(boolean includeParticipants) {
    var result = new ArrayList<String>();
    if (!accountSid.matches("AC[0-9a-fA-F]{32}")) result.add("Account SID");
    if (authToken.isBlank() || authToken.startsWith("REEMPLAZAR")) result.add("Auth Token");
    if (!sender.matches("whatsapp:\\+[1-9][0-9]{6,14}")) result.add("Número de WhatsApp de prueba");
    try {
      var uri = URI.create(publicBaseUrl);
      if (!"https".equals(uri.getScheme())
          || uri.getHost() == null
          || uri.getRawQuery() != null
          || uri.getFragment() != null
          || !Set.of("", "/").contains(uri.getPath())
          || uri.getUserInfo() != null) result.add("Dirección HTTPS pública");
    } catch (IllegalArgumentException e) {
      result.add("Dirección HTTPS pública");
    }
    if (includeParticipants
        && (allowedParticipants.isEmpty()
            || allowedParticipants.stream().anyMatch(p -> !p.matches("\\+[1-9][0-9]{6,14}"))))
      result.add("Participantes autorizados");
    if (!Set.of("TEXT", "TEMPLATE").contains(sendMode)) result.add("Modo de envío");
    return List.copyOf(result);
  }

  public boolean ready() {
    return enabled && missing().isEmpty();
  }

  public boolean callbackReady() {
    return enabled && missing(false).isEmpty();
  }

  public boolean templateReady() {
    return testTemplateSid.matches("HX[0-9a-fA-F]{32}");
  }
}
