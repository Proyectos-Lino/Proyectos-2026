package com.odontocare.kapso.service;

import com.odontocare.integrations.service.IntegrationBoundary;
import com.odontocare.whatsapp.model.WhatsAppMessage;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class KapsoDispatchService {
  private final IntegrationBoundary boundary;
  private final KapsoMessagingService messages;
  private final KapsoSender sender;

  public KapsoDispatchService(
      IntegrationBoundary boundary, KapsoMessagingService messages, KapsoSender sender) {
    this.boundary = boundary;
    this.messages = messages;
    this.sender = sender;
  }

  @Transactional(timeout = 15)
  public void dispatch(WhatsAppMessage message) {
    // Credential updates wait for this bounded send to finish, then retire older tasks.
    if (!boundary.message(message.id())) return;
    String phone = messages.destination(message);
    if (phone != null) messages.finish(message.id(), sender.send(message, phone));
  }
}
