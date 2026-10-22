package com.odontocare.integrations.service;

import com.odontocare.agent.service.AgentInterrupted;
import com.odontocare.integrations.model.IntegrationValues;
import com.odontocare.integrations.repository.IntegrationRepository;
import com.odontocare.shared.web.ApiException;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Shared database lock lasts until the business transaction completes. */
@Component
public class IntegrationBoundary {
  private final IntegrationRepository repository;
  private final RuntimeIntegrationState state;

  public IntegrationBoundary(IntegrationRepository repository, RuntimeIntegrationState state) {
    this.repository = repository;
    this.state = state;
  }

  public boolean managed() {
    return state.managed();
  }

  public IntegrationValues lock() {
    var current = state.current();
    if (repository.lockShared() != current.version() || state.locked())
      throw ApiException.conflict("La integración está cambiando. Actualiza e inténtalo de nuevo.");
    return current;
  }

  public void requireRun(UUID run) {
    if (!managed()) return;
    var current = lock();
    if (!current.aiEnabled() || repository.taskRevision("agent_run", run) != current.version())
      throw new AgentInterrupted();
  }

  public boolean message(UUID message) {
    if (!managed()) return true;
    var current = lock();
    return current.whatsappEnabled()
        && repository.taskRevision("kapso_message", message) == current.version();
  }

  public void receive() {
    if (managed() && !lock().whatsappEnabled())
      throw new ApiException(
          org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE, "WhatsApp está desactivado.");
  }
}
