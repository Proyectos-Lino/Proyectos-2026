package com.odontocare.agent.config;

import java.util.*;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties("odontocare.ai")
public class AgentProperties {
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

  public String getProvider() {
    return provider;
  }

  public void setProvider(String value) {
    provider = value.strip().toUpperCase(Locale.ROOT);
  }

  public String getModel() {
    return model;
  }

  public void setModel(String value) {
    model = value.strip();
  }

  public String getApiKey() {
    return apiKey;
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
    if (!provider.equals("GROQ")) fields.add("Proveedor GROQ");
    if (model.isBlank() || model.length() > 100) fields.add("Modelo");
    if (!List.of("low", "medium", "high").contains(reasoningEffort))
      fields.add("Esfuerzo del modelo (low, medium, high)");
    if (apiKey.isBlank() || apiKey.startsWith("REEMPLAZAR")) fields.add("Clave privada de Groq");
    if (maxModelCalls < 1 || maxModelCalls > 8) fields.add("Llamadas por mensaje (1–8)");
    if (maxCompletionTokens < 256 || maxCompletionTokens > 4096)
      fields.add("Tokens de salida (256–4096)");
    if (requestTimeoutSeconds < 3 || requestTimeoutSeconds > 30)
      fields.add("Tiempo por llamada (3–30 s)");
    if (runTimeoutSeconds < requestTimeoutSeconds || runTimeoutSeconds > 180)
      fields.add("Tiempo por mensaje (máximo 180 s)");
    if (contextMessages < 1 || contextMessages > 12) fields.add("Contexto (1–12 mensajes)");
    if (debounceMilliseconds < 0 || debounceMilliseconds > 5000)
      fields.add("Agrupación de mensajes (0–5000 ms)");
    return List.copyOf(fields);
  }

  public boolean ready() {
    return enabled && missing().isEmpty();
  }
}
