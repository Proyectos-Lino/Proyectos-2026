package com.odontocare.agent.service;

import com.odontocare.agent.model.AgentRun;
import com.odontocare.agent.repository.*;
import com.odontocare.shared.web.ApiException;
import java.text.Normalizer;
import java.time.Clock;
import java.time.LocalDate;
import java.util.*;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AgentIdentityService {
  private final AgentInboxRepository inbox;
  private final AgentCatalogRepository catalog;
  private final AgentSupervisionRepository supervision;
  private final AgentIdentityRepository repository;
  private final Clock clock;

  public AgentIdentityService(
      AgentInboxRepository inbox,
      AgentCatalogRepository catalog,
      AgentSupervisionRepository supervision,
      AgentIdentityRepository repository,
      Clock clock) {
    this.inbox = inbox;
    this.catalog = catalog;
    this.supervision = supervision;
    this.repository = repository;
    this.clock = clock;
  }

  @Transactional
  public Map<String, Object> verify(AgentRun run, String name, String relationship) {
    if (!Set.of("SELF", "GUARDIAN").contains(relationship))
      throw ApiException.badRequest(
          "Confirma si la cita es para ti o si eres el responsable del paciente.");
    String normalized = normalize(name);
    if (normalized.split(" ").length < 2)
      throw ApiException.badRequest("Solicita el nombre completo del paciente.");
    var message = inbox.message(run.messageId(), false).orElseThrow();
    var recent = repository.recent(run.conversationId(), message.source(), run.messageId());
    boolean explicit =
        recent.stream()
            .map(AgentIdentityService::normalize)
            .anyMatch(
                text ->
                    text.contains(normalized)
                        && (relationship.equals("SELF")
                            ? text.matches("(?s).*(para mi|soy |mi nombre|me llamo).*")
                            : text.matches(
                                "(?s).*(mi hij[oa]|mis hijos|soy .*tutor|soy .*responsable|soy"
                                    + " .*madre|soy .*padre).*")));
    if (!explicit)
      throw ApiException.badRequest(
          "Pregunta el nombre completo y confirma expresamente si es para el contacto o para su"
              + " hijo como responsable.");
    var conversation = inbox.conversation(run.conversationId(), false).orElseThrow();
    var matches =
        catalog.exactPatients(conversation.phone(), normalized).stream()
            .filter(p -> normalize(p.get("full_name").toString()).equals(normalized))
            .toList();
    if (matches.size() > 1)
      throw ApiException.conflict(
          "La identidad requiere revisión de recepción; no se seleccionará una ficha"
              + " automáticamente.");
    UUID patient = matches.isEmpty() ? null : (UUID) matches.getFirst().get("id");
    if (patient != null) requireRelationship(patient, conversation.phone(), relationship);
    supervision.initialize(run.conversationId(), message.source());
    supervision.verify(
        run.conversationId(),
        message.source(),
        patient,
        name.strip(),
        relationship,
        clock.instant());
    var result = new LinkedHashMap<String, Object>();
    result.put("verified", true);
    result.put("patient_name", name.strip());
    result.put("patient_id", patient);
    result.put("relationship", relationship);
    result.put("provisional", patient == null);
    return result;
  }

  @Transactional(readOnly = true)
  public Optional<Map<String, String>> continuationArguments(AgentRun run) {
    var message = inbox.message(run.messageId(), false).orElseThrow();
    return supervision
        .verified(run.conversationId(), message.source(), clock.instant())
        .filter(binding -> "GUARDIAN".equals(binding.get("relationship")))
        .filter(
            binding ->
                Pattern.compile("\\bmi hij[oa]\\b")
                        .matcher(normalize(message.body()))
                        .results()
                        .count()
                    == 1)
        .filter(
            binding ->
                Pattern.compile(
                        "\\bmi hij[oa] "
                            + Pattern.quote(normalize(binding.get("patient_name").toString()))
                            + "(?:$| (?:soy|el|con|para|por|a las)\\b)")
                    .matcher(normalize(message.body()))
                    .find())
        .map(
            binding ->
                Map.of(
                    "patient_name",
                    binding.get("patient_name").toString(),
                    "relationship",
                    "GUARDIAN"));
  }

  @Transactional
  public Map<String, Object> require(AgentRun run, UUID patient, String name) {
    var message = inbox.message(run.messageId(), false).orElseThrow();
    var binding =
        supervision
            .verified(run.conversationId(), message.source(), clock.instant())
            .orElseThrow(
                () ->
                    ApiException.badRequest(
                        "Primero confirma para quién es la cita y la relación del contacto."));
    if (!Objects.equals(binding.get("patient_id"), patient)
        || !normalize(binding.get("patient_name").toString()).equals(normalize(name)))
      throw ApiException.forbidden();
    if (patient != null) {
      String phone = inbox.conversation(run.conversationId(), false).orElseThrow().phone();
      catalog.patient(phone, patient).orElseThrow(ApiException::forbidden);
      requireRelationship(patient, phone, binding.get("relationship").toString());
    }
    return binding;
  }

  public void requireRelationship(UUID patient, String phone, String relationship) {
    if (relationship.equals("GUARDIAN") && !repository.guardian(patient, phone)
        || relationship.equals("SELF") && repository.minor(patient, LocalDate.now(clock)))
      throw ApiException.forbidden();
  }

  public static String normalize(String text) {
    return Normalizer.normalize(text, Normalizer.Form.NFD)
        .replaceAll("\\p{M}", "")
        .toLowerCase(Locale.ROOT)
        .replaceAll("[^a-z0-9]+", " ")
        .strip();
  }
}
