package com.odontocare.agent.service;

import java.util.*;
import org.springframework.stereotype.Component;

@Component
public class AgentToolDefinitions {
  public List<Map<String, Object>> available(List<Map<String, Object>> evidence) {
    return available(evidence, "");
  }

  public List<Map<String, Object>> available(List<Map<String, Object>> evidence, String request) {
    boolean verified =
        evidence.stream()
            .anyMatch(
                fact ->
                    fact.get("name").equals("verificar_paciente")
                        && fact.get("result") instanceof Map<?, ?> result
                        && Boolean.TRUE.equals(result.get("verified")));
    boolean ownAppointments = hasItems(evidence, "consultar_mis_citas");
    boolean ownConsulted =
        evidence.stream().anyMatch(fact -> fact.get("name").equals("consultar_mis_citas"));
    boolean allowsProposal = !AgentConsent.forbidsProposal(request);
    boolean changing =
        allowsProposal
            && AgentIdentityService.normalize(request).matches("(?s).*(reprogram|cancel|anul).*");
    boolean ownConsultation = AgentAdministrativeIntent.ownAppointmentConsultation(request);
    if (ownConsultation && !verified)
      return all().stream()
          .filter(
              tool ->
                  Set.of("verificar_paciente", "derivar_recepcion")
                      .contains(((Map<?, ?>) tool.get("function")).get("name")))
          .toList();
    if (verified && (changing || ownConsultation) && !ownConsulted)
      return all().stream()
          .filter(
              tool -> ((Map<?, ?>) tool.get("function")).get("name").equals("consultar_mis_citas"))
          .toList();
    boolean slots = hasItems(evidence, "consultar_horarios");
    boolean service = hasItems(evidence, "consultar_servicios") || ownAppointments;
    return all().stream()
        .filter(
            tool -> {
              String name = ((Map<?, ?>) tool.get("function")).get("name").toString();
              return switch (name) {
                case "verificar_paciente", "pacientes_contacto" -> !verified;
                case "consultar_mis_citas" -> verified;
                case "consultar_horarios" -> service && (!changing || ownAppointments);
                case "proponer_reprogramacion" ->
                    allowsProposal && verified && ownAppointments && slots;
                case "proponer_cancelacion" -> allowsProposal && verified && ownAppointments;
                case "proponer_cita" -> allowsProposal && verified && slots && !changing;
                default -> true;
              };
            })
        .toList();
  }

  private boolean hasItems(List<Map<String, Object>> evidence, String tool) {
    return evidence.stream()
        .anyMatch(
            fact ->
                fact.get("name").equals(tool)
                    && fact.get("result") instanceof Map<?, ?> result
                    && result.get("items") instanceof List<?> items
                    && !items.isEmpty());
  }

  public List<Map<String, Object>> all() {
    return List.of(
        tool(
            "verificar_paciente",
            "Verifica el nombre completo expresamente informado y la relación del contacto"
                + " autenticado. SELF=para mí; GUARDIAN=mi hijo y soy su responsable. No adivines"
                + " nombres ni relaciones.",
            Map.of(
                "patient_name",
                text("Nombre completo informado"),
                "relationship",
                text("SELF o GUARDIAN")),
            List.of("patient_name", "relationship")),
        tool(
            "consultar_mis_citas",
            "Citas propias verificadas; devuelve appointment_ref, service_id y dentist_id para"
                + " cambios, sin consultar el catálogo de nuevo. search filtra servicio o"
                + " profesional; usa vacío para todas las propias, nunca el nombre del paciente.",
            Map.of(
                "search",
                text(
                    "Una palabra del servicio o profesional, o vacío. No usar el nombre del"
                        + " paciente."),
                "page",
                integer("Página desde 0")),
            List.of("search")),
        tool(
            "proponer_reprogramacion",
            "Prepara un cambio solicitado expresamente. No cambia la cita hasta confirmar el"
                + " resumen. Consultar_mis_citas y consultar_horarios antes.",
            Map.of(
                "appointment_ref",
                text("Referencia temporal de consultar_mis_citas"),
                "slot_id",
                text("Horario devuelto para esta reprogramación"),
                "reason",
                text("Motivo informado del cambio")),
            List.of("appointment_ref", "slot_id", "reason")),
        tool(
            "proponer_cancelacion",
            "Prepara cancelación expresamente solicitada de una cita propia verificada; requiere"
                + " confirmación posterior.",
            Map.of(
                "appointment_ref",
                text("Referencia temporal de consultar_mis_citas"),
                "reason",
                text("Motivo informado de cancelación")),
            List.of("appointment_ref", "reason")),
        tool(
            "derivar_recepcion",
            "Pausa el agente y deriva a recepción solicitudes clínicas, reclamos, identidad dudosa"
                + " o casos no resolubles. No diagnostiques.",
            Map.of("reason", text("Motivo administrativo sin diagnósticos ni datos sensibles")),
            List.of("reason")),
        tool(
            "consultar_servicios",
            "Busca servicios activos reservables y sus precios. Usa una palabra corta como"
                + " limpieza; páginas de 5.",
            Map.of("search", text("Texto de búsqueda"), "page", integer("Página desde 0")),
            List.of("search")),
        tool(
            "pacientes_contacto",
            "Busca fichas activas vinculadas al teléfono autenticado, sin historia clínica. Pide al"
                + " usuario para quién es la cita; no asumas su identidad.",
            Map.of("search", text("Nombre para buscar o vacío"), "page", integer("Página desde 0")),
            List.of("search")),
        tool(
            "consultar_horarios",
            "Consulta horarios reales de un servicio. Devuelve referencias slot_id que puedes"
                + " proponer. Para mañana usa days_from_today=1. Si la hora está ocupada devuelve"
                + " alternativas.",
            Map.of(
                "service_id",
                text("ID del servicio obtenido del catálogo"),
                "dentist_id",
                text("ID opcional del profesional; omitir para consultar disponibles"),
                "dentist_name",
                text(
                    "Nombre solicitado, por ejemplo Julia Huaytalla. Usa este campo si no conoces"
                        + " su ID; nunca pongas un nombre en dentist_id."),
                "date",
                text("Fecha absoluta YYYY-MM-DD, omitir si es relativa"),
                "days_from_today",
                integer("Días desde hoy: hoy=0, mañana=1, pasado mañana=2"),
                "preferred_time",
                text("Hora HH:mm opcional"),
                "appointment_ref",
                text(
                    "Referencia de consultar_mis_citas para reprogramación; conserva duración y"
                        + " excluye solo esa cita")),
            List.of("service_id")),
        tool(
            "proponer_cita",
            "Prepara resumen y código de confirmación para un horario consultado. NO reserva."
                + " Requiere paciente identificado o nombre completo informado para ficha"
                + " provisional.",
            Map.of(
                "slot_id",
                text("Referencia recibida en consultar_horarios"),
                "patient_id",
                text("ID del paciente del contacto, omitir para paciente nuevo"),
                "patient_name",
                text("Nombre completo expresamente indicado por el usuario")),
            List.of("slot_id", "patient_name")),
        tool(
            "descartar_propuesta",
            "Descarta la propuesta pendiente si el usuario niega la reserva o cambia los datos. No"
                + " cancela citas ya creadas.",
            Map.of(),
            List.of()));
  }

  private Map<String, Object> text(String description) {
    return Map.of("type", "string", "description", description);
  }

  private Map<String, Object> integer(String description) {
    return Map.of("type", "integer", "description", description);
  }

  private Map<String, Object> tool(
      String name,
      String description,
      Map<String, Map<String, Object>> properties,
      List<String> required) {
    var nullableProperties = new LinkedHashMap<String, Object>();
    properties.forEach(
        (field, schema) -> {
          var property = new LinkedHashMap<String, Object>(schema);
          if (!required.contains(field))
            property.put("type", List.of(property.get("type"), "null"));
          nullableProperties.put(field, property);
        });
    return Map.of(
        "type",
        "function",
        "function",
        Map.of(
            "name",
            name,
            "description",
            description,
            "parameters",
            Map.of(
                "type",
                "object",
                "properties",
                nullableProperties,
                "required",
                required,
                "additionalProperties",
                false)));
  }
}
