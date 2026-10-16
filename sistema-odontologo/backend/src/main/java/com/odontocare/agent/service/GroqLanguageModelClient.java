package com.odontocare.agent.service;

import com.odontocare.agent.config.AgentProperties;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Component
public class GroqLanguageModelClient implements LanguageModelClient {
  private static final int MAX_ERROR_BODY_LENGTH = 65_536;
  private final AgentProperties config;
  private final ObjectMapper mapper;
  private final URI endpoint;
  private final HttpClient client =
      HttpClient.newBuilder()
          .connectTimeout(Duration.ofSeconds(4))
          .followRedirects(HttpClient.Redirect.NEVER)
          .build();

  @Autowired
  public GroqLanguageModelClient(AgentProperties config, ObjectMapper mapper) {
    this(config, mapper, URI.create("https://api.groq.com/openai/v1/chat/completions"));
  }

  GroqLanguageModelClient(AgentProperties config, ObjectMapper mapper, URI endpoint) {
    this.config = config;
    this.mapper = mapper;
    this.endpoint = endpoint;
  }

  public Reply reply(List<Map<String, Object>> messages, List<Map<String, Object>> tools) {
    if (!config.ready())
      throw new ModelFailure("CONFIGURATION", "Activa el agente y completa su clave privada.");
    var body = new LinkedHashMap<String, Object>();
    body.put("model", config.getModel());
    body.put("messages", messages);
    body.put("tools", tools);
    body.put("tool_choice", "auto");
    body.put("parallel_tool_calls", false);
    body.put("reasoning_effort", config.getReasoningEffort());
    body.put("temperature", 0);
    body.put("include_reasoning", false);
    body.put("max_completion_tokens", config.getMaxCompletionTokens());
    body.put("stream", false);
    var request =
        HttpRequest.newBuilder(endpoint)
            .timeout(Duration.ofSeconds(config.getRequestTimeoutSeconds()))
            .header("Authorization", "Bearer " + config.getApiKey())
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body)))
            .build();
    try {
      var response = client.send(request, HttpResponse.BodyHandlers.ofString());
      if (response.statusCode() < 200 || response.statusCode() >= 300)
        throw providerFailure(response);
      var json = mapper.readTree(response.body());
      var choice = json.path("choices").path(0);
      if (choice.isMissingNode() || choice.path("finish_reason").asString("").equals("length"))
        throw new ModelFailure(
            "OUTPUT_LIMIT",
            "El modelo agotó la salida permitida. Revisa el límite de tokens y reintenta.");
      var message = choice.path("message");
      var calls = new ArrayList<ToolCall>();
      for (var call : message.path("tool_calls")) {
        if (!call.path("type").asString("").equals("function") || calls.size() >= 4)
          throw new ModelFailure(
              "INVALID_RESPONSE", "El modelo devolvió herramientas no admitidas.");
        calls.add(
            new ToolCall(
                call.path("id").asString(""),
                call.path("function").path("name").asString(""),
                call.path("function").path("arguments").asString("")));
      }
      String content = message.path("content").asString("");
      if (content.length() > 6000)
        throw new ModelFailure("INVALID_RESPONSE", "La respuesta excede el tamaño admitido.");
      return new Reply(
          content,
          List.copyOf(calls),
          Math.max(0, json.path("usage").path("prompt_tokens").asInt(0)),
          Math.max(0, json.path("usage").path("completion_tokens").asInt(0)));
    } catch (ModelFailure failure) {
      throw failure;
    } catch (InterruptedException failure) {
      Thread.currentThread().interrupt();
      throw new ModelFailure(
          "INTERRUPTED", "El procesamiento fue interrumpido; puede reintentarse.");
    } catch (java.net.http.HttpTimeoutException failure) {
      throw new ModelFailure(
          "TIMEOUT", "Groq no respondió a tiempo; puede reintentarse sin duplicar reservas.");
    } catch (Exception failure) {
      throw new ModelFailure(
          "CONNECTION",
          "No se pudo obtener una respuesta válida de Groq. Revisa conexión y configuración.");
    }
  }

  private ModelFailure providerFailure(HttpResponse<String> response) {
    var diagnostics = new LinkedHashMap<String, Object>();
    diagnostics.put("http_status", response.statusCode());
    String providerCode = "OTHER";
    if (response.body().length() <= MAX_ERROR_BODY_LENGTH) {
      try {
        var error = mapper.readTree(response.body()).path("error");
        String code = error.path("code").asString("");
        String type = error.path("type").asString("");
        if (Set.of(
                "tool_use_failed",
                "rate_limit_exceeded",
                "model_not_found",
                "context_length_exceeded")
            .contains(code)) providerCode = code;
        if (Set.of(
                "invalid_request_error",
                "tokens",
                "requests",
                "server_error",
                "authentication_error")
            .contains(type)) diagnostics.put("provider_type", type);
        if (response.statusCode() == 429) {
          // Classify only known quota names. Never retain the provider's free-form message.
          String detail = error.path("message").asString("").toLowerCase(Locale.ROOT);
          diagnostics.put("limit_kind", limitKind(detail));
        }
      } catch (RuntimeException ignored) {
        // The HTTP status remains useful even when an upstream error body is malformed.
      }
    }
    diagnostics.put("provider_code", providerCode);
    int status = response.statusCode();
    String code = failureCode(status, providerCode);
    String detail =
        switch (code) {
          case "AUTHENTICATION" ->
              "Groq rechazó la clave o el permiso del modelo. Revisa la cuenta y reinicia el"
                  + " backend.";
          case "RATE_LIMIT" ->
              "Groq limitó temporalmente las llamadas. Se conserva el análisis completado; revisa"
                  + " los límites si persiste.";
          case "TOOL_GENERATION" ->
              "Groq rechazó una llamada a herramientas generada por el modelo. No se ejecutó esa"
                  + " llamada.";
          case "PROVIDER_UNAVAILABLE" ->
              "Groq no pudo atender la llamada por un fallo temporal del proveedor.";
          default ->
              "Groq rechazó la petición. Revisa el estado HTTP y la categoría registrados en la"
                  + " bitácora.";
        };
    return new ModelFailure(code, detail, status == 429 ? retryAfter(response) : 2, diagnostics);
  }

  private String failureCode(int status, String providerCode) {
    if (status == 401 || status == 403) return "AUTHENTICATION";
    if (status == 429) return "RATE_LIMIT";
    if ((status == 400 || status == 422) && providerCode.equals("tool_use_failed"))
      return "TOOL_GENERATION";
    if (status >= 500 && status < 600) return "PROVIDER_UNAVAILABLE";
    return "PROVIDER_ERROR";
  }

  private String limitKind(String detail) {
    if (detail.contains("tokens per day") || detail.contains("(tpd)")) return "TOKENS_PER_DAY";
    if (detail.contains("tokens per minute") || detail.contains("(tpm)"))
      return "TOKENS_PER_MINUTE";
    if (detail.contains("requests per day") || detail.contains("(rpd)")) return "REQUESTS_PER_DAY";
    if (detail.contains("requests per minute") || detail.contains("(rpm)"))
      return "REQUESTS_PER_MINUTE";
    return "UNKNOWN";
  }

  private long retryAfter(HttpResponse<?> response) {
    try {
      double seconds =
          Double.parseDouble(response.headers().firstValue("retry-after").orElse("30"));
      return Double.isFinite(seconds) && seconds > 0 ? (long) Math.ceil(seconds) : 30;
    } catch (NumberFormatException ignored) {
      return 30;
    }
  }
}
