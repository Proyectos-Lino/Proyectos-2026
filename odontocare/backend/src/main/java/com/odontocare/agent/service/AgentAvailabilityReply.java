package com.odontocare.agent.service;

import java.time.LocalDateTime;
import java.util.*;
import tools.jackson.databind.JsonNode;

/** Safe availability text when an unverified booking claim would obscure the tool's options. */
final class AgentAvailabilityReply {
  private String response;

  void record(String name, Object arguments, Object result) {
    if (!name.equals("consultar_horarios")
        || !(arguments instanceof JsonNode args)
        || !(result instanceof Map<?, ?> data)
        || !(data.get("items") instanceof List<?> items)) return;
    String preferred =
        Objects.toString(data.get("preferred_time"), args.path("preferred_time").asString(""));
    boolean occupied =
        !preferred.isBlank() && Boolean.FALSE.equals(data.get("preferred_time_available"));
    var text =
        new StringBuilder(
            occupied
                ? "El horario de " + preferred + " no está disponible.\n"
                : "Estos son los horarios disponibles:\n");
    if (items.isEmpty()) text.append("No hay horarios disponibles para la fecha consultada.\n");
    for (Object item : items.stream().limit(5).toList()) {
      if (!(item instanceof Map<?, ?> slot)) continue;
      var start = LocalDateTime.parse(slot.get("local_start").toString());
      var end = LocalDateTime.parse(slot.get("local_end").toString());
      text.append("• ")
          .append(start.toLocalDate())
          .append(" ")
          .append(start.toLocalTime())
          .append("–")
          .append(end.toLocalTime())
          .append(" · ")
          .append(slot.get("dentist_name"))
          .append(" · ")
          .append(slot.get("service_name"))
          .append("\n");
    }
    response =
        text.append(
                items.isEmpty()
                    ? "\n¿Quieres consultar otra fecha? No se creó ni cambió ninguna cita."
                    : "\n"
                        + "¿Cuál de estos horarios prefieres? Todavía no se creó ni cambió"
                        + " ninguna cita.")
            .toString();
  }

  Optional<String> response() {
    return Optional.ofNullable(response);
  }
}
