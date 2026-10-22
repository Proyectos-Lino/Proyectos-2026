package com.odontocare.agent.service;

import com.odontocare.agent.model.AgentRun;
import com.odontocare.agent.repository.*;
import com.odontocare.audit.service.AuditService;
import com.odontocare.kapso.repository.KapsoRepository;
import com.odontocare.shared.web.ApiException;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AgentReplyService {
  private final AgentRepository runs;
  private final AgentInboxRepository inbox;
  private final KapsoRepository kapso;
  private final AuditService audit;
  private final Clock clock;
  private final AgentSupervisionService supervision;

  public AgentReplyService(
      AgentRepository runs,
      AgentInboxRepository inbox,
      KapsoRepository kapso,
      AuditService audit,
      AgentSupervisionService supervision,
      Clock clock) {
    this.runs = runs;
    this.inbox = inbox;
    this.kapso = kapso;
    this.audit = audit;
    this.clock = clock;
    this.supervision = supervision;
  }

  @Transactional
  public void handoff(AgentRun run, String text, String reason) {
    complete(run.id(), text);
    kapso.markHandoffNotice(run.id());
    supervision.handoff(run, reason);
  }

  @Transactional
  public void complete(UUID id, String text) {
    var initial = runs.get(id, false).orElseThrow();
    supervision.requireAutomatic(initial);
    var run = runs.get(id, true).orElseThrow();
    if (!Objects.equals(
        runs.latestInbound(run.conversationId(), run.messageId()), run.messageId())) {
      runs.group(id, null, clock.instant());
      runs.proposalByRun(id)
          .filter(p -> p.state().equals("PENDING"))
          .ifPresent(p -> runs.proposalState(p.id(), "SUPERSEDED"));
      return;
    }
    runs.finish(id, "COMPLETED", text, null, null, clock.instant());
    supervision.completed(run, "RESPONDED");
    enqueue(run, text);
  }

  @Transactional
  public void error(UUID id, String code, String detail, String response) {
    var initial = runs.get(id, false).orElseThrow();
    supervision.requireAutomatic(initial);
    var run = runs.get(id, true).orElseThrow();
    runs.finish(id, "FAILED", response, code, detail, clock.instant());
    if (Objects.equals(runs.latestInbound(run.conversationId(), run.messageId()), run.messageId()))
      enqueue(run, response);
  }

  private void enqueue(AgentRun run, String text) {
    var input = inbox.message(run.messageId(), false).orElseThrow();
    if (!inbox.provider(run.conversationId()).equals("KAPSO") || input.source().equals("APP_TEST"))
      return;
    if (text.isBlank() || text.length() > 1600)
      throw ApiException.badRequest("La respuesta del agente supera el límite permitido.");
    UUID key =
        UUID.nameUUIDFromBytes(
            ("agent-reply:" + run.id() + ":" + run.attempts()).getBytes(StandardCharsets.UTF_8));
    kapso.keyLock(key);
    var message =
        kapso
            .byKey(key)
            .orElseGet(() -> kapso.enqueueAgent(run.conversationId(), text, key, clock.instant()));
    runs.attachReply(run.id(), message.id());
    runs.step(
        run.id(),
        "TOOL",
        "guardar_respuesta",
        Map.of("reply_id", message.id()),
        Map.of("status", message.status(), "automatic", true),
        "OK",
        clock.instant());
    kapso.touch(run.conversationId(), text, clock.instant(), false);
    audit.recordAs(
        null,
        "Agente IA",
        "AGENT_REPLY_QUEUED",
        "KAPSO_MESSAGE",
        message.id(),
        "Guardó respuesta autónoma vinculada a la solicitud.");
  }
}
