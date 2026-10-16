package com.odontocare.agent.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
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
    String date = Objects.toString(data.get("date"), "");
    String day =
        date.isBlank()
            ? "la fecha consultada"
            : LocalDate.parse(date)
                .format(
                    DateTimeFormatter.ofPattern("EEEE dd/MM/yyyy", Locale.forLanguageTag("es-PE")));
    if (Boolean.FALSE.equals(data.get("professional_found"))) {
      response =
          "No encontré un profesional habilitado con los datos indicados. Confirma con qué"
              + " odontólogo deseas atenderte para consultar sus horarios. Todavía no se creó ni"
              + " cambió ninguna cita.";
      return;
    }
    if (items.isEmpty()) {
      response =
          "No hay horarios disponibles para el "
              + day
              + ".\n\n¿Quieres consultar otra fecha? Todavía no se creó ni cambió ninguna cita.";
      return;
    }
    var text =
        new StringBuilder(
            occupied
                ? "El horario de "
                    + preferred
                    + " no está disponible para el "
                    + day
                    + ". Estos son otros horarios:\n"
                : "Estos son los horarios disponibles para el " + day + ":\n");
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
