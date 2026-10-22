package com.odontocare.agent.controller;

import com.odontocare.agent.dto.SupervisionContracts.*;
import com.odontocare.agent.service.AgentSupervisionService;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/whatsapp")
public class AgentSupervisionController {
  private final AgentSupervisionService service;
  private final com.odontocare.agent.service.AgentInboxQueryService inbox;

  public AgentSupervisionController(
      AgentSupervisionService service, com.odontocare.agent.service.AgentInboxQueryService inbox) {
    this.service = service;
    this.inbox = inbox;
  }

  @GetMapping("/agent/inbox")
  @PreAuthorize("hasAuthority('WHATSAPP_READ')")
  public com.odontocare.shared.pagination.PageResponse<java.util.Map<String, Object>> inbox(
      @Valid @ModelAttribute com.odontocare.shared.pagination.PageQuery query,
      @RequestParam(defaultValue = "") String mode,
      @RequestParam(defaultValue = "") String state) {
    return inbox.list(query, mode, state);
  }

  @GetMapping("/agent/policy")
  @PreAuthorize("hasAuthority('WHATSAPP_READ')")
  public Policy policy() {
    return service.policy();
  }

  @PutMapping("/agent/policy")
  @PreAuthorize("hasAuthority('SETTINGS_WRITE')")
  public Policy save(@Valid @RequestBody Policy p) {
    return service.save(p);
  }

  @GetMapping("/conversations/{id}/supervision")
  @PreAuthorize("hasAuthority('WHATSAPP_READ')")
  public Context context(
      @PathVariable UUID id, @RequestParam(defaultValue = "KAPSO") String source) {
    return service.context(id, source);
  }

  @PostMapping("/conversations/{id}/control")
  @PreAuthorize("hasAuthority('AGENT_CONTROL_WRITE')")
  public Context control(@PathVariable UUID id, @Valid @RequestBody Control c) {
    return service.control(id, c);
  }
}
