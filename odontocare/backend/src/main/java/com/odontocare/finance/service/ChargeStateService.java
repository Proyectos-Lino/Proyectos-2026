package com.odontocare.finance.service;

import com.odontocare.finance.repository.ChargeStateRepository;
import com.odontocare.shared.web.ApiException;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class ChargeStateService {
  private final ChargeStateRepository states;

  public ChargeStateService(ChargeStateRepository states) {
    this.states = states;
  }

  public void requireOpen(UUID id) {
    if (states.closed(id))
      throw ApiException.conflict(
          "El cargo está finalizado. Reábrelo antes de modificarlo o devolver su dinero aplicado.");
  }

  public boolean closed(UUID id) {
    return states.closed(id);
  }
}
