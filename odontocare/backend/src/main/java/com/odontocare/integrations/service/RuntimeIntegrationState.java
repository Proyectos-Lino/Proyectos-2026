package com.odontocare.integrations.service;

import com.odontocare.integrations.model.IntegrationValues;
import com.odontocare.integrations.repository.IntegrationRepository;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Component
public class RuntimeIntegrationState {
  private final IntegrationRepository repository;
  private final IntegrationCipher cipher;
  private final ObjectMapper mapper;
  private final Environment environment;
  private volatile IntegrationValues current = IntegrationValues.empty(0);
  private volatile boolean locked;

  public RuntimeIntegrationState(
      IntegrationRepository repository,
      IntegrationCipher cipher,
      ObjectMapper mapper,
      Environment environment) {
    this.repository = repository;
    this.cipher = cipher;
    this.mapper = mapper;
    this.environment = environment;
  }

  public boolean managed() {
    return environment.getProperty("odontocare.integrations.managed", Boolean.class, true);
  }

  public IntegrationValues current() {
    return current;
  }

  public boolean locked() {
    return locked;
  }

  public java.util.UUID accountScope() {
    return managed() ? current.accountScope() : new java.util.UUID(0, 0);
  }

  @EventListener(ApplicationReadyEvent.class)
  public void refresh() {
    if (!managed()) return;
    var saved = repository.get(false);
    try {
      current =
          saved.encrypted() == null
              ? IntegrationValues.empty(saved.version())
              : mapper.readValue(cipher.decrypt(saved.encrypted()), IntegrationValues.class);
      if (current.version() != saved.version()) throw new IllegalStateException();
      locked = false;
    } catch (Exception failure) {
      current = IntegrationValues.empty(saved.version());
      locked = true;
    }
  }

  public void apply(IntegrationValues values) {
    current = values;
    locked = false;
  }

  public String fixedPublicUrl() {
    return environment
        .getProperty("odontocare.integrations.public-base-url", "")
        .strip()
        .replaceAll("/+$", "");
  }
}
