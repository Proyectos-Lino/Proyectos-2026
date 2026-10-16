package com.odontocare.agent.service;

import com.odontocare.agent.dto.SupervisionContracts.*;
import com.odontocare.agent.model.AgentRun;
import com.odontocare.agent.repository.*;
import com.odontocare.audit.service.AuditService;
import com.odontocare.installation.repository.InstallationProfileRepository;
import com.odontocare.security.model.AccountPrincipal;
import com.odontocare.shared.web.ApiException;
import java.time.*;
import java.util.*;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AgentSupervisionService {
  private final AgentSupervisionRepository repository;
  private final AgentInboxRepository inbox;
  private final AgentRepository runs;
  private final AuditService audit;
  private final Clock clock;
  private final InstallationProfileRepository profiles;

  public AgentSupervisionService(
      AgentSupervisionRepository repository,
      AgentInboxRepository inbox,
      AgentRepository runs,
      AuditService audit,
      Clock clock,
      InstallationProfileRepository profiles) {
    this.repository = repository;
    this.inbox = inbox;
    this.runs = runs;
    this.audit = audit;
    this.clock = clock;
    this.profiles = profiles;
  }

  @Transactional(readOnly = true)
  public Policy policy() {
    return repository.policy(false);
  }

  @Transactional
  public Policy save(Policy p) {
    if (!repository.policy(true).version().equals(p.version()))
      throw ApiException.conflict("La configuración cambió; vuelve a cargarla.");
    for (var period : p.schedule()) {
      if (period.startMinute() >= period.endMinute())
        throw ApiException.badRequest("El horario debe terminar después de comenzar.");
      if (p.schedule().stream()
          .anyMatch(
              other ->
                  other != period
                      && other.dayOfWeek() == period.dayOfWeek()
                      && other.startMinute() < period.endMinute()
                      && period.startMinute() < other.endMinute()))
        throw ApiException.badRequest("Los horarios del agente no pueden solaparse.");
    }
    repository.savePolicy(p);
    audit.record(
        "AGENT_POLICY_UPDATED",
        "AGENT_POLICY",
        1,
        "Actualizó horarios, textos y reglas administrativas del agente.");
    return repository.policy(false);
  }

  @Transactional
  public Context context(UUID id, String source) {
    if (!Set.of("KAPSO", "TWILIO", "APP_TEST").contains(source))
      throw ApiException.badRequest("Canal de solicitud inválido.");
    inbox.conversation(id, false).orElseThrow(ApiException::notFound);
    repository.initialize(id, source);
    repository.expire(clock.instant());
    return repository.context(id, source);
  }

  @Transactional
  public Context control(UUID id, Control request) {
    inbox.conversation(id, true).orElseThrow(ApiException::notFound);
    String source = inbox.provider(id);
    repository.initialize(id, source);
    var current = repository.control(id, true);
    if (((Number) current.get("generation")).longValue() != request.generation())
      throw ApiException.conflict("El control cambió; actualiza la conversación.");
    var auth = SecurityContextHolder.getContext().getAuthentication();
    UUID actor =
        auth != null && auth.getPrincipal() instanceof AccountPrincipal p ? p.getId() : null;
    repository.control(
        id,
        request.mode(),
        request.mode().equals("HUMAN") ? actor : null,
        request.reason().strip(),
        clock.instant());
    if (request.mode().equals("AUTO")) repository.release(id, clock.instant());
    else {
      repository.pause(id, clock.instant());
      repository.suppressNotices(id);
    }
    audit.record(
        "AGENT_CONTROL_" + request.mode(),
        "AGENT_CONVERSATION",
        id,
        "Cambió control de conversación; solicitudes anteriores no se ejecutan al devolverlo.");
    return repository.context(id, source);
  }

  @Transactional
  public void register(AgentRun run, String provider) {
    String source = inbox.message(run.messageId(), false).orElseThrow().source();
    repository.initialize(run.conversationId(), source);
    repository.attachRun(run.id(), run.conversationId(), provider);
    if (!repository.control(run.conversationId(), false).get("mode").equals("AUTO"))
      repository.pause(run.conversationId(), clock.instant());
  }

  @Transactional
  public void requireAutomatic(AgentRun run) {
    inbox.conversation(run.conversationId(), true).orElseThrow();
    String source = inbox.message(run.messageId(), false).orElseThrow().source();
    repository.initialize(run.conversationId(), source);
    repository.control(run.conversationId(), true);
    if (!repository.matches(run.id(), run.conversationId())) throw new AgentInterrupted();
  }

  @Transactional
  public void requireAction(AgentRun run) {
    requireAutomatic(run);
    repository.policy(true);
    if (!openNow())
      throw ApiException.conflict(
          "El agente dejó de estar disponible; recepción revisará la solicitud.");
  }

  @Transactional
  public void completed(AgentRun run, String outcome) {
    repository.outcome(run.id(), outcome);
  }

  @Transactional
  public void handoff(AgentRun run, String reason) {
    if (inbox.message(run.messageId(), false).orElseThrow().source().equals("APP_TEST")) {
      request(run, "REFERRED", reason, null, "", null);
      repository.outcome(run.id(), "REFERRED");
      return;
    }
    inbox.conversation(run.conversationId(), true).orElseThrow();
    repository.control(run.conversationId(), true);
    repository.control(run.conversationId(), "HANDOFF", null, reason, clock.instant());
    repository.pause(run.conversationId(), clock.instant());
    repository.outcome(run.id(), "REFERRED");
    audit.recordAs(
        null,
        "Agente IA",
        "AGENT_HANDOFF",
        "AGENT_CONVERSATION",
        run.conversationId(),
        "Derivó la conversación a recepción: " + reason);
  }

  public boolean openNow() {
    var policy = repository.policy(false);
    if (!policy.enabled()) return false;
    if (policy.schedule().isEmpty()) return true;
    var now =
        clock.instant().atZone(ZoneId.of(profiles.findById((short) 1).orElseThrow().getTimeZone()));
    int minute = now.getHour() * 60 + now.getMinute();
    return policy.schedule().stream()
        .anyMatch(
            p ->
                p.dayOfWeek() == now.getDayOfWeek().getValue()
                    && p.startMinute() <= minute
                    && minute < p.endMinute());
  }

  public void request(
      AgentRun run, String state, String summary, UUID patient, String name, UUID appointment) {
    String source = inbox.message(run.messageId(), false).orElseThrow().source();
    repository.request(
        run.conversationId(), source, state, summary, patient, name, appointment, clock.instant());
  }
}
