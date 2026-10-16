package com.odontocare.whatsapp.service;

import com.odontocare.kapso.config.KapsoProperties;
import com.odontocare.shared.web.ApiException;
import com.odontocare.whatsapp.config.WhatsAppProperties;
import com.twilio.security.RequestValidator;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.MultiValueMap;

@Service
public class WhatsAppSignatureService {
  private final WhatsAppProperties config;
  private final KapsoProperties kapso;

  public WhatsAppSignatureService(WhatsAppProperties config, KapsoProperties kapso) {
    this.config = config;
    this.kapso = kapso;
  }

  public Map<String, String> validate(
      String path, String rawQuery, String signature, MultiValueMap<String, String> form) {
    if (kapso.isEnabled()) throw ApiException.forbidden();
    if (!(path.endsWith("/status") ? config.callbackReady() : config.ready()))
      throw new ApiException(
          HttpStatus.SERVICE_UNAVAILABLE,
          "La conexión de WhatsApp no está habilitada o falta configurarla.");
    var parameters = new HashMap<String, String>();
    for (var entry : form.entrySet()) {
      if (entry.getValue().size() != 1)
        throw ApiException.badRequest("El evento contiene campos repetidos.");
      parameters.put(entry.getKey(), entry.getValue().getFirst());
    }
    String url = config.url(path) + (rawQuery == null ? "" : "?" + rawQuery);
    if (signature == null
        || !new RequestValidator(config.getAuthToken()).validate(url, parameters, signature))
      throw ApiException.forbidden();
    if (!config.getAccountSid().equals(parameters.get("AccountSid")))
      throw ApiException.forbidden();
    return parameters;
  }
}
