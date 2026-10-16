package com.odontocare.agent.service;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;

public class AgentAppointmentReply {
  private String response;

  public void record(String name, Object result) {
    if (!name.equals("consultar_mis_citas")
        || !(result instanceof Map<?, ?> data)
        || !(data.get("items") instanceof List<?> items)) return;
    var lines = new ArrayList<String>();
    for (var item : items)
      if (item instanceof Map<?, ?> a)
        lines.add(
            "• "
                + a.get("patient_name")
                + ": "
                + a.get("service_name")
                + " con "
                + a.get("dentist_name")
                + ", "
                + LocalDateTime.parse(a.get("local_start").toString())
                    .format(
                        DateTimeFormatter.ofPattern(
                            "EEEE dd/MM/yyyy HH:mm", Locale.forLanguageTag("es")))
                + ". Duración: "
                + a.get("duration_minutes")
                + " minutos.");
    response =
        lines.isEmpty()
            ? (data.get("search_applied") instanceof String search && !search.isBlank()
                ? "No encontré próximas citas del paciente verificado que coincidan con la"
                    + " búsqueda. Puedes consultar sin filtro o indicar solo el servicio o el"
                    + " profesional."
                : "No encontré próximas citas activas del paciente verificado.")
            : "Próximas citas del paciente verificado:\n"
                + String.join("\n", lines)
                + "\nSi deseas un cambio, indica cuál cita y el motivo.";
  }

  public Optional<String> response() {
    return Optional.ofNullable(response);
  }
}
