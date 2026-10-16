package com.odontocare.installation.service;

import com.odontocare.installation.dto.SettingsRequest;
import com.odontocare.shared.web.ApiException;
import java.time.ZoneId;
import java.util.Currency;

public final class SettingsValidator {
  private SettingsValidator() {}

  public static void validate(SettingsRequest request) {
    if (!ZoneId.getAvailableZoneIds().contains(request.timeZone()))
      throw ApiException.badRequest(
          "Selecciona una zona horaria IANA válida, por ejemplo America/Lima.");
    try {
      Currency.getInstance(request.currency());
    } catch (IllegalArgumentException exception) {
      throw ApiException.badRequest("La moneda debe ser un código ISO válido, por ejemplo PEN.");
    }
    if (contrast(request.brandColor(), "#ffffff") < 5.0
        || contrast(request.brandColor(), request.accentColor()) < 4.5)
      throw ApiException.badRequest(
          "El color principal debe permitir texto blanco legible. Elige un tono más oscuro.");
    if (contrast(request.accentColor(), "#263b35") < 4.5
        || contrast(request.accentColor(), "#53664f") < 4.5) {
      throw ApiException.badRequest(
          "El color de fondo debe permitir leer los textos oscuros. Elige un tono más claro.");
    }
  }

  private static double contrast(String first, String second) {
    double a = luminance(first), b = luminance(second);
    return (Math.max(a, b) + 0.05) / (Math.min(a, b) + 0.05);
  }

  private static double luminance(String hex) {
    double[] values = new double[3];
    for (int index = 0; index < 3; index++) {
      double value = Integer.parseInt(hex.substring(1 + index * 2, 3 + index * 2), 16) / 255.0;
      values[index] = value <= 0.04045 ? value / 12.92 : Math.pow((value + 0.055) / 1.055, 2.4);
    }
    return 0.2126 * values[0] + 0.7152 * values[1] + 0.0722 * values[2];
  }
}
