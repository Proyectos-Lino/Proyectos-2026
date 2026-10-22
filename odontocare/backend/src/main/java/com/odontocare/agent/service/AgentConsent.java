package com.odontocare.agent.service;

public final class AgentConsent {
  private AgentConsent() {}

  public static boolean forbidsProposal(String body) {
    return AgentIdentityService.normalize(body)
        .matches(
            "(?s).*(no (?:me"
                + " )?(?:reserves|reservar|canceles|cancelar|reprogrames|reprogramar|confirmo)|sin"
                + " (?:reservar|cancelar|reprogramar)|solo (?:quiero )?(?:informacion|consultar"
                + " disponibilidad|ver las opciones)|todavia no confirmo).*");
  }

  public static boolean matchesAction(String body, String action) {
    if (AgentConfirmation.code(body).isPresent()) return true;
    String text = AgentIdentityService.normalize(body);
    if (text.contains("cancelacion")) return action.equals("CANCEL");
    if (text.contains("reprogramacion") || text.contains("el cambio"))
      return action.equals("RESCHEDULE");
    if (text.contains("reservar")) return action.equals("BOOK");
    return AgentConfirmation.natural(body);
  }
}
