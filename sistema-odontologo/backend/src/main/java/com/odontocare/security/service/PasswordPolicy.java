package com.odontocare.security.service;

import com.odontocare.shared.web.ApiException;
import java.nio.charset.StandardCharsets;

public final class PasswordPolicy {
  private PasswordPolicy() {}

  public static void requireValid(String password) {
    if (password == null
        || password.length() < 10
        || password.getBytes(StandardCharsets.UTF_8).length > 72
        || password.isBlank()) {
      throw ApiException.badRequest(
          "La contraseña debe tener al menos 10 caracteres y ocupar como máximo 72 bytes.");
    }
  }
}
