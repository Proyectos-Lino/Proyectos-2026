package com.odontocare.agent.service;

public final class AgentAdministrativeIntent {
  private AgentAdministrativeIntent() {}

  public static boolean clinical(String body) {
    String text = AgentIdentityService.normalize(body);
    return text.matches(
        "(?s).*(historial"
            + " clinico|diagnostic|recet|medicamento|dolor|sangrado|hinchazon|urgencia|cuanto"
            + " debo|saldo|deuda|descuento|reembolso).*");
  }

  public static boolean human(String body) {
    return AgentIdentityService.normalize(body)
        .matches(
            "(?s).*(hablar con (?:una persona|recepcion|el profesional|un humano)|quiero (?:un"
                + " humano|hacer un reclamo)|reclam|queja).*");
  }

  public static boolean changed(String body) {
    return AgentIdentityService.normalize(body)
        .matches(
            "(?s).*(cambie de opinion|ahora prefiero|mejor (?:para|cancel)|ya no quiero|otra"
                + " persona|otro paciente).*");
  }

  public static boolean ownAppointmentConsultation(String body) {
    String text = AgentIdentityService.normalize(body);
    boolean query = text.matches("(?s).*(consult|ver |muest|cuando|a que hora|que citas).*");
    boolean own = text.matches("(?s).*\\b(?:mi|mis|su|sus) (?:proxim[ao]s? )?citas?\\b.*");
    boolean information =
        text.matches(
            "(?s).*(solo (?:quiero )?(?:consultar|ver|saber)|sin (?:hacer )?cambios|no"
                + " (?:quiero |deseo )?cambiar).*");
    return query
        && own
        && !text.matches("(?s).*(horari|disponib|precio|cuesta).*")
        && (information || !text.matches("(?s).*(reprogram|cancel|anul|cambi|mover|reserv).*"));
  }

  public static boolean asksIdentity(String body) {
    return AgentIdentityService.normalize(body)
        .matches("(?s).*(nombre completo|para quien|relacion|responsable|tutor).*");
  }
}
