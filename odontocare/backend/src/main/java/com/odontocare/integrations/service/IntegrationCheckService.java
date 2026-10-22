package com.odontocare.integrations.service;

import com.odontocare.integrations.dto.IntegrationContracts.ProbeResult;
import com.odontocare.shared.web.ApiException;
import java.util.Set;
import org.springframework.stereotype.Service;

@Service
public class IntegrationCheckService {
  private final RuntimeIntegrationState state;
  private final IntegrationProbe probe;

  public IntegrationCheckService(RuntimeIntegrationState state, IntegrationProbe probe) {
    this.state = state;
    this.probe = probe;
  }

  public ProbeResult check(String provider, long version) {
    if (!Set.of("kapso", "groq").contains(provider)) throw ApiException.notFound();
    var values = state.current();
    if (values.version() != version)
      throw ApiException.conflict("La configuración cambió. Recarga antes de comprobarla.");
    if (state.locked())
      throw ApiException.conflict("Revisa la clave de cifrado de la instalación.");
    var result = probe.check(provider, values);
    if (state.current().version() != version)
      return new ProbeResult(
          false,
          "La configuración cambió durante la comprobación. Recarga y comprueba los valores"
              + " actuales.");
    return result;
  }
}
