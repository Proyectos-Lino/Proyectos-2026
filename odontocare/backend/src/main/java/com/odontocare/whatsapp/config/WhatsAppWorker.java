package com.odontocare.whatsapp.config;

import com.odontocare.kapso.config.KapsoProperties;
import com.odontocare.whatsapp.service.*;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.*;

@Configuration
@EnableScheduling
public class WhatsAppWorker {
  private final WhatsAppProperties config;
  private final WhatsAppOutboxService outbox;
  private final TwilioSender sender;
  private final KapsoProperties kapso;

  public WhatsAppWorker(
      WhatsAppProperties config,
      WhatsAppOutboxService outbox,
      TwilioSender sender,
      KapsoProperties kapso) {
    this.config = config;
    this.outbox = outbox;
    this.sender = sender;
    this.kapso = kapso;
  }

  @Scheduled(fixedDelay = 4000, initialDelay = 4000)
  public void sendPending() {
    if (kapso.isEnabled() || !config.ready() || !config.isWorkerEnabled()) return;
    try {
      outbox
          .claim()
          .ifPresent(
              message -> {
                String phone = outbox.destination(message);
                if (phone != null) outbox.finish(message.id(), sender.send(message, phone));
              });
    } catch (RuntimeException e) {
      org.slf4j.LoggerFactory.getLogger(WhatsAppWorker.class)
          .warn("WhatsApp delivery task unavailable: {}", e.getClass().getSimpleName());
    }
  }
}
