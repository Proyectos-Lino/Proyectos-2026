package com.odontocare.agent.service;

import java.text.Normalizer;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

public final class AgentConfirmation {
  private AgentConfirmation() {}

  private static final Pattern CODE =
      Pattern.compile("^CONFIRMO\\s+([A-F0-9]{8})[.!]?$", Pattern.CASE_INSENSITIVE);

  public static Optional<String> code(String text) {
    var match = CODE.matcher(text.strip());
    return match.matches()
        ? Optional.of(match.group(1).toUpperCase(Locale.ROOT))
        : Optional.empty();
  }

  public static boolean natural(String text) {
    if (text.contains("?") || text.contains("¿")) return false;
    String normalized = normalize(text);
    return normalized.matches("(?:si )?confirmo(?: (?:la|esa|esta) cita)?(?: por favor)?")
        || normalized.matches(
            "(?:si )?confirmo (?:la cancelacion|la reprogramacion|el cambio)(?: de (?:la|mi)"
                + " cita)?(?: por favor)?")
        || normalized.matches("si quiero reservar (?:la|esa|esta) cita");
  }

  public static boolean ambiguous(String text) {
    return normalize(text).matches("si|ok|vale|de acuerdo|claro|confirmo");
  }

  private static String normalize(String text) {
    return Normalizer.normalize(text, Normalizer.Form.NFD)
        .replaceAll("\\p{M}", "")
        .toLowerCase(Locale.ROOT)
        .replaceAll("[¡!¿?,.;:]", " ")
        .strip()
        .replaceAll("\\s+", " ");
  }
}
