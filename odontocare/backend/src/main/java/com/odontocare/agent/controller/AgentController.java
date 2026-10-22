package com.odontocare.agent.controller;

import com.odontocare.agent.dto.AgentContracts.*;
import com.odontocare.agent.model.AgentRun;
import com.odontocare.agent.service.AgentQueueService;
import com.odontocare.shared.pagination.*;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/whatsapp")
public class AgentController {
  private final AgentQueueService queue;

  public AgentController(AgentQueueService queue) {
    this.queue = queue;
  }

  @GetMapping("/agent/configuration")
  @PreAuthorize("hasAuthority('WHATSAPP_READ')")
  public Configuration configuration() {
    return queue.configuration();
  }

  @PostMapping("/agent/test-messages")
  @PreAuthorize("hasAuthority('AGENT_TEST_WRITE')")
  public TestResult test(@Valid @RequestBody TestMessage request) {
    return queue.test(request);
  }

  @GetMapping("/conversations/{id}/agent/runs")
  @PreAuthorize("hasAuthority('WHATSAPP_READ')")
  public PageResponse<AgentRun> runs(
      @PathVariable UUID id,
      @Valid @ModelAttribute PageQuery query,
      @RequestParam(required = false) String state) {
    return queue.runs(id, query, state);
  }

  @GetMapping("/conversations/{id}/agent/proposal")
  @PreAuthorize("hasAuthority('WHATSAPP_READ')")
  public Proposal proposal(@PathVariable UUID id) {
    return queue.proposal(id);
  }

  @GetMapping("/agent/runs/{id}")
  @PreAuthorize("hasAuthority('WHATSAPP_READ')")
  public AgentQueueService.Detail detail(@PathVariable UUID id) {
    return queue.detail(id);
  }

  @GetMapping("/agent/runs/{id}/steps")
  @PreAuthorize("hasAuthority('WHATSAPP_READ')")
  public PageResponse<Step> steps(
      @PathVariable UUID id,
      @Valid @ModelAttribute PageQuery query,
      @RequestParam(required = false) String kind) {
    return queue.steps(id, query, kind);
  }

  @PostMapping("/agent/runs/{id}/retry")
  @PreAuthorize("hasAuthority('AGENT_TEST_WRITE')")
  public AgentRun retry(@PathVariable UUID id) {
    return queue.retry(id);
  }

  @PostMapping("/agent/runs/{id}/reply/retry")
  @PreAuthorize("hasAuthority('WHATSAPP_WRITE')")
  public com.odontocare.whatsapp.dto.WhatsAppContracts.Message retryReply(@PathVariable UUID id) {
    return queue.retryReply(id);
  }
}
