package com.odontocare.kapso.config;

import com.odontocare.kapso.service.*;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class KapsoWorker {
  private final KapsoProperties config;
  private final KapsoMessagingService messages;
  private final KapsoDispatchService dispatch;

  public KapsoWorker(
      KapsoProperties config, KapsoMessagingService messages, KapsoDispatchService dispatch) {
    this.config = config;
    this.messages = messages;
    this.dispatch = dispatch;
  }

  @Scheduled(fixedDelay = 4000, initialDelay = 4000)
  public void sendPending() {
    if (!config.ready() || !config.isWorkerEnabled()) return;
    try {
      messages.claim().ifPresent(dispatch::dispatch);
    } catch (RuntimeException failure) {
      org.slf4j.LoggerFactory.getLogger(KapsoWorker.class)
          .warn("Kapso delivery task unavailable: {}", failure.getClass().getSimpleName());
    }
  }
}
