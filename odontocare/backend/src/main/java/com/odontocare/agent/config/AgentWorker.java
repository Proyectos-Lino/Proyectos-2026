package com.odontocare.agent.config;

import com.odontocare.agent.service.*;
import com.odontocare.kapso.config.KapsoProperties;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class AgentWorker {
  private final AgentProperties config;
  private final AgentQueueService queue;
  private final BookingAgent agent;
  private final KapsoProperties kapso;

  public AgentWorker(
      AgentProperties config, AgentQueueService queue, BookingAgent agent, KapsoProperties kapso) {
    this.config = config;
    this.queue = queue;
    this.agent = agent;
    this.kapso = kapso;
  }

  @Scheduled(fixedDelay = 4000, initialDelay = 8000)
  public void processPending() {
    if ((kapso.isEnabled() && (!kapso.isAgentEnabled() || !kapso.ready()))
        || !config.ready()
        || !config.isWorkerEnabled()) return;
    try {
      queue.claim().ifPresent(agent::process);
    } catch (RuntimeException failure) {
      org.slf4j.LoggerFactory.getLogger(AgentWorker.class)
          .warn("Agent task unavailable: {}", failure.getClass().getSimpleName());
    }
  }
}
