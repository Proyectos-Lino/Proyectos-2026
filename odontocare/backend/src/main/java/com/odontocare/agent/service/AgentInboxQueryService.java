package com.odontocare.agent.service;

import com.odontocare.agent.repository.*;
import com.odontocare.kapso.config.KapsoProperties;
import com.odontocare.shared.pagination.*;
import com.odontocare.shared.web.ApiException;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AgentInboxQueryService {
  private final AgentInboxQueryRepository inbox;
  private final AgentSupervisionRepository supervision;
  private final KapsoProperties config;
  private final java.time.Clock clock;

  public AgentInboxQueryService(
      AgentInboxQueryRepository inbox,
      AgentSupervisionRepository supervision,
      KapsoProperties config,
      java.time.Clock clock) {
    this.inbox = inbox;
    this.supervision = supervision;
    this.config = config;
    this.clock = clock;
  }

  @Transactional
  public PageResponse<Map<String, Object>> list(PageQuery query, String mode, String state) {
    if (!mode.isBlank() && !Set.of("AUTO", "HUMAN", "HANDOFF", "CLOSED").contains(mode)
        || !state.isBlank()
            && !Set.of(
                    "INFORMATION_PENDING",
                    "OPTIONS_OFFERED",
                    "CONFIRMATION_PENDING",
                    "COMPLETED",
                    "EXPIRED",
                    "REFERRED")
                .contains(state)) throw ApiException.badRequest("Filtro de bandeja inválido.");
    supervision.expire(clock.instant());
    return inbox.list(config.getPhoneNumberId(), query, mode, state);
  }
}
