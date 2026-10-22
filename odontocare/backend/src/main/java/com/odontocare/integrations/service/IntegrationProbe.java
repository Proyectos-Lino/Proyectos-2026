package com.odontocare.integrations.service;

import com.odontocare.integrations.dto.IntegrationContracts.ProbeResult;
import com.odontocare.integrations.model.IntegrationValues;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/** Read-only checks against fixed official hosts; never sends a message or inference. */
@Component
public class IntegrationProbe {
  private final ObjectMapper mapper;
  private final URI kapsoBase;
  private final URI groqBase;
  private final HttpClient client =
      HttpClient.newBuilder()
          .connectTimeout(Duration.ofSeconds(4))
          .followRedirects(HttpClient.Redirect.NEVER)
          .build();

  @org.springframework.beans.factory.annotation.Autowired
  public IntegrationProbe(ObjectMapper mapper) {
    this(
        mapper,
        URI.create("https://api.kapso.ai/meta/whatsapp/v24.0/"),
        URI.create("https://api.groq.com/openai/v1/models/"));
  }

  IntegrationProbe(ObjectMapper mapper, URI kapsoBase, URI groqBase) {
    this.mapper = mapper;
    this.kapsoBase = kapsoBase;
    this.groqBase = groqBase;
  }

  public ProbeResult check(String provider, IntegrationValues values) {
    boolean kapso = provider.equals("kapso");
    String key = kapso ? values.kapsoKey() : values.groqKey();
    if (key.isBlank() || kapso && values.phoneNumberId().isBlank())
      return new ProbeResult(false, "Guarda primero la clave y los datos requeridos.");
    URI url = kapso ? kapsoBase.resolve(values.phoneNumberId()) : groqBase.resolve(values.model());
    var request =
        HttpRequest.newBuilder(url)
            .timeout(Duration.ofSeconds(8))
            .header(kapso ? "X-API-Key" : "Authorization", kapso ? key : "Bearer " + key)
            .GET()
            .build();
    try {
      var response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
      try (var body = response.body()) {
        if (response.statusCode() != 200)
          return new ProbeResult(
              false,
              switch (response.statusCode()) {
                case 401, 403 -> "El proveedor rechazó la clave o sus permisos.";
                case 402 -> "El proveedor requiere revisar el saldo o la facturación.";
                case 404 -> "El número o el modelo no está disponible para esta cuenta.";
                case 429 -> "El proveedor pidió esperar antes de otra comprobación.";
                default -> "El proveedor no pudo completar la comprobación. Inténtalo más tarde.";
              });
        byte[] bytes = body.readNBytes(65537);
        if (bytes.length > 65536)
          return new ProbeResult(false, "La respuesta del proveedor no pudo verificarse.");
        var json = mapper.readTree(bytes);
        String expected = kapso ? values.phoneNumberId() : values.model();
        if (!expected.equals(json.path("id").asString("")))
          return new ProbeResult(false, "La respuesta no coincide con la configuración guardada.");
        if (kapso
            && json.hasNonNull("display_phone_number")
            && !json.path("display_phone_number")
                .asString()
                .replaceAll("[^+0-9]", "")
                .equals(values.sender()))
          return new ProbeResult(
              false, "El número visible no corresponde al identificador configurado.");
        return new ProbeResult(
            true,
            kapso
                ? "Kapso reconoce la clave y el número. La recepción y entrega reales requieren"
                    + " probar el webhook desde WhatsApp."
                : "GroqCloud reconoce la clave y el modelo. No se generó una respuesta ni se"
                    + " consumieron tokens de inferencia.");
      }
    } catch (InterruptedException failure) {
      Thread.currentThread().interrupt();
      return new ProbeResult(false, "La comprobación fue interrumpida.");
    } catch (Exception failure) {
      return new ProbeResult(
          false, "No se pudo conectar al proveedor. Revisa la conexión e inténtalo de nuevo.");
    }
  }
}
