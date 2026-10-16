package com.odontocare.kapso.config;

import com.odontocare.kapso.service.*;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@org.springframework.context.annotation.Profile("messaging")
@Component
public class KapsoWorker {
  private final KapsoProperties config;
  private final KapsoMessagingService messages;
  private final KapsoSender sender;

  public KapsoWorker(KapsoProperties config, KapsoMessagingService messages, KapsoSender sender) {
    this.config = config;
    this.messages = messages;
    this.sender = sender;
  }

  @Scheduled(fixedDelay = 4000, initialDelay = 4000)
  public void sendPending() {
    if (!config.ready() || !config.isWorkerEnabled()) return;
    try {
      messages
          .claim()
          .ifPresent(
              message -> {
                String phone = messages.destination(message);
                if (phone != null) messages.finish(message.id(), sender.send(message, phone));
              });
    } catch (RuntimeException failure) {
      org.slf4j.LoggerFactory.getLogger(KapsoWorker.class)
          .warn("Kapso delivery task unavailable: {}", failure.getClass().getSimpleName());
    }
  }
}
