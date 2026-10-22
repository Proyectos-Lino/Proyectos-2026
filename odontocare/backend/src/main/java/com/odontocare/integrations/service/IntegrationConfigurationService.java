package com.odontocare.integrations.service;

import com.odontocare.audit.service.AuditService;
import com.odontocare.integrations.dto.IntegrationContracts.*;
import com.odontocare.integrations.model.IntegrationValues;
import com.odontocare.integrations.repository.IntegrationRepository;
import com.odontocare.shared.web.ApiException;
import java.net.URI;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.*;
import tools.jackson.databind.ObjectMapper;

@Service
public class IntegrationConfigurationService {
  private final IntegrationRepository repository;
  private final RuntimeIntegrationState state;
  private final IntegrationCipher cipher;
  private final ObjectMapper mapper;
  private final AuditService audit;

  public IntegrationConfigurationService(
      IntegrationRepository repository,
      RuntimeIntegrationState state,
      IntegrationCipher cipher,
      ObjectMapper mapper,
      AuditService audit) {
    this.repository = repository;
    this.state = state;
    this.cipher = cipher;
    this.mapper = mapper;
    this.audit = audit;
  }

  @Transactional(readOnly = true)
  public View get() {
    return view(state.current());
  }

  public View view(IntegrationValues v) {
    String url = state.fixedPublicUrl().isBlank() ? v.publicBaseUrl() : state.fixedPublicUrl();
    return new View(
        v.version(),
        v.whatsappEnabled(),
        v.aiEnabled(),
        !v.kapsoKey().isBlank(),
        v.phoneNumberId(),
        v.sender(),
        !v.webhookSecret().isBlank(),
        url,
        url.isBlank() ? "" : url + "/api/v1/integrations/kapso/events",
        !state.fixedPublicUrl().isBlank(),
        v.restrictParticipants(),
        v.participants(),
        !v.groqKey().isBlank(),
        v.model(),
        cipher.available(),
        state.locked());
  }

  @Transactional
  public View save(Save r) {
    var stored = repository.get(true);
    state.refresh();
    if (stored.version() != r.version())
      throw ApiException.conflict("La configuración cambió. Recarga antes de guardar.");
    if (state.locked() || !cipher.available())
      throw ApiException.conflict(
          "No se pueden recuperar o cifrar las credenciales. Revisa la clave de cifrado de la"
              + " instalación.");
    var old = state.current();
    String base =
        state.fixedPublicUrl().isBlank()
            ? r.publicBaseUrl().strip().replaceAll("/+$", "")
            : state.fixedPublicUrl();
    if (!base.isBlank()) validateUrl(base);
    String phone = r.phoneNumberId().strip(), sender = r.sender().strip();
    if (!phone.isBlank() && !phone.matches("[0-9]{5,30}"))
      throw ApiException.badRequest("El identificador del número debe contener de 5 a 30 dígitos.");
    if (!sender.isBlank() && !sender.matches("\\+[1-9][0-9]{7,14}"))
      throw ApiException.badRequest("El número de WhatsApp debe incluir + y el código de país.");
    var next =
        new IntegrationValues(
            old.version(),
            r.whatsappEnabled(),
            r.aiEnabled(),
            secret(r.kapsoKey(), r.clearKapsoKey(), old.kapsoKey()),
            phone,
            sender,
            secret(r.webhookSecret(), r.clearWebhookSecret(), old.webhookSecret()),
            base,
            r.restrictParticipants(),
            r.participants().stream().distinct().toList(),
            secret(r.groqKey(), r.clearGroqKey(), old.groqKey()),
            r.model(),
            old.accountScope());
    if (next.whatsappEnabled()
        && (next.kapsoKey().isBlank()
            || next.phoneNumberId().isBlank()
            || next.sender().isBlank()
            || next.webhookSecret().length() < 16
            || base.isBlank()
            || next.restrictParticipants() && next.participants().isEmpty()))
      throw ApiException.badRequest(
          "Completa la clave de Kapso, el número y su ID, el secreto del webhook, la dirección"
              + " HTTPS y los participantes autorizados antes de activar WhatsApp.");
    if (next.aiEnabled() && next.groqKey().isBlank())
      throw ApiException.badRequest("Guarda una clave de GroqCloud antes de activar el agente.");
    if (next.equals(old)) return view(old);
    boolean accountChanged =
        !old.kapsoKey().equals(next.kapsoKey())
            || !old.phoneNumberId().equals(next.phoneNumberId());
    if (accountChanged
        && !next.kapsoKey().isBlank()
        && !old.webhookSecret().isBlank()
        && old.webhookSecret().equals(next.webhookSecret()))
      throw ApiException.badRequest(
          "Al cambiar la clave de Kapso o el identificador del número, configura también un secreto"
              + " de webhook nuevo aquí y en Kapso. Así no se aceptan eventos de la cuenta"
              + " anterior.");
    UUID scope = accountChanged ? UUID.randomUUID() : old.accountScope();
    var updated =
        new IntegrationValues(
            old.version() + 1,
            next.whatsappEnabled(),
            next.aiEnabled(),
            next.kapsoKey(),
            next.phoneNumberId(),
            next.sender(),
            next.webhookSecret(),
            base,
            next.restrictParticipants(),
            next.participants(),
            next.groqKey(),
            next.model(),
            scope);
    repository.save(updated.version(), cipher.encrypt(mapper.writeValueAsBytes(updated)));
    repository.retirePending();
    TransactionSynchronizationManager.registerSynchronization(
        new TransactionSynchronization() {
          @Override
          public void afterCommit() {
            state.apply(updated);
          }
        });
    audit.record(
        "INTEGRATIONS_UPDATED",
        "INTEGRATIONS",
        1,
        "Actualizó conexiones y activación; retiró tareas anteriores y conservó las citas"
            + " registradas.");
    return view(updated);
  }

  private String secret(String input, boolean clear, String old) {
    if (clear && input != null && !input.isBlank())
      throw ApiException.badRequest("Elige entre reemplazar o eliminar la credencial.");
    String result = clear ? "" : input == null || input.isBlank() ? old : input.strip();
    if (result.chars().anyMatch(c -> c < 33 || c > 126))
      throw ApiException.badRequest("La credencial contiene caracteres no válidos.");
    return result;
  }

  private void validateUrl(String value) {
    try {
      var uri = URI.create(value);
      if (!"https".equals(uri.getScheme())
          || uri.getHost() == null
          || uri.getUserInfo() != null
          || !uri.getPath().isEmpty()
          || uri.getQuery() != null
          || uri.getFragment() != null
          || uri.getPort() > 65535) throw new IllegalArgumentException();
    } catch (Exception failure) {
      throw ApiException.badRequest(
          "La dirección pública debe ser un origen HTTPS, sin rutas ni parámetros.");
    }
  }
}
