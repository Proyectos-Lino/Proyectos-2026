package com.odontocare.agent.config;

import java.util.*;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties("odontocare.ai")
public class AgentProperties {
  @org.springframework.beans.factory.annotation.Autowired(required = false)
  private com.odontocare.integrations.service.RuntimeIntegrationState integrations;

  public boolean managed() {
    return integrations != null && integrations.managed();
  }

  private boolean enabled, workerEnabled = true;
  private String provider = "GROQ", model = "openai/gpt-oss-20b", apiKey = "";
  private String reasoningEffort = "low";
  private int maxModelCalls = 6,
      maxCompletionTokens = 768,
      requestTimeoutSeconds = 12,
      runTimeoutSeconds = 75,
      contextMessages = 6;
  private int debounceMilliseconds = 1500;

  public int getDebounceMilliseconds() {
    return debounceMilliseconds;
  }

  public String getReasoningEffort() {
    return reasoningEffort;
  }

  public void setReasoningEffort(String value) {
    reasoningEffort = value.strip().toLowerCase(Locale.ROOT);
  }

  public void setDebounceMilliseconds(int value) {
    debounceMilliseconds = value;
  }

  public boolean isEnabled() {
    return managed() ? integrations.current().aiEnabled() : enabled;
  }

  public void setEnabled(boolean value) {
    enabled = value;
  }

  public boolean isWorkerEnabled() {
    return workerEnabled && (!managed() || integrations.current().aiEnabled());
  }

  public void setWorkerEnabled(boolean value) {
    workerEnabled = value;
  }

  public String getProvider() {
    return provider;
  }

  public void setProvider(String value) {
    provider = value.strip().toUpperCase(Locale.ROOT);
  }

  public String getModel() {
    return managed() ? integrations.current().model() : model;
  }

  public void setModel(String value) {
    model = value.strip();
  }

  public String getApiKey() {
    return managed() ? integrations.current().groqKey() : apiKey;
  }

  public void setApiKey(String value) {
    apiKey = value.strip();
  }

  public int getMaxModelCalls() {
    return maxModelCalls;
  }

  public void setMaxModelCalls(int value) {
    maxModelCalls = value;
  }

  public int getMaxCompletionTokens() {
    return maxCompletionTokens;
  }

  public void setMaxCompletionTokens(int value) {
    maxCompletionTokens = value;
  }

  public int getRequestTimeoutSeconds() {
    return requestTimeoutSeconds;
  }

  public void setRequestTimeoutSeconds(int value) {
    requestTimeoutSeconds = value;
  }

  public int getRunTimeoutSeconds() {
    return runTimeoutSeconds;
  }

  public void setRunTimeoutSeconds(int value) {
    runTimeoutSeconds = value;
  }

  public int getContextMessages() {
    return contextMessages;
  }

  public void setContextMessages(int value) {
    contextMessages = value;
  }

  public List<String> missing() {
    var fields = new ArrayList<String>();
    if (!getProvider().equals("GROQ")) fields.add("Proveedor GROQ");
    if (getModel().isBlank() || getModel().length() > 100) fields.add("Modelo");
    if (!List.of("low", "medium", "high").contains(getReasoningEffort()))
      fields.add("Esfuerzo del modelo (low, medium, high)");
    if (getApiKey().isBlank() || getApiKey().startsWith("REEMPLAZAR"))
      fields.add("Clave privada de Groq");
    if (getMaxModelCalls() < 1 || getMaxModelCalls() > 8) fields.add("Llamadas por mensaje (1–8)");
    if (getMaxCompletionTokens() < 256 || getMaxCompletionTokens() > 4096)
      fields.add("Tokens de salida (256–4096)");
    if (getRequestTimeoutSeconds() < 3 || getRequestTimeoutSeconds() > 30)
      fields.add("Tiempo por llamada (3–30 s)");
    if (getRunTimeoutSeconds() < getRequestTimeoutSeconds() || getRunTimeoutSeconds() > 180)
      fields.add("Tiempo por mensaje (máximo 180 s)");
    if (getContextMessages() < 1 || getContextMessages() > 12)
      fields.add("Contexto (1–12 mensajes)");
    if (getDebounceMilliseconds() < 0 || getDebounceMilliseconds() > 5000)
      fields.add("Agrupación de mensajes (0–5000 ms)");
    return List.copyOf(fields);
  }

  public AgentProperties snapshot() {
    AgentProperties copy = new AgentProperties();
    var values = managed() ? integrations.current() : null;
    copy.enabled = values == null ? enabled : values.aiEnabled();
    copy.workerEnabled = isWorkerEnabled();
    copy.apiKey = values == null ? apiKey : values.groqKey();
    copy.model = values == null ? model : values.model();
    copy.provider = provider;
    copy.reasoningEffort = reasoningEffort;
    copy.maxModelCalls = maxModelCalls;
    copy.maxCompletionTokens = maxCompletionTokens;
    copy.requestTimeoutSeconds = requestTimeoutSeconds;
    copy.runTimeoutSeconds = runTimeoutSeconds;
    copy.contextMessages = contextMessages;
    copy.debounceMilliseconds = debounceMilliseconds;
    return copy;
  }

  public boolean ready() {
    return isEnabled() && missing().isEmpty();
  }
}
