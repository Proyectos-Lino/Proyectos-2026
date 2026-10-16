package com.odontocare.whatsapp.service;

import com.odontocare.kapso.config.KapsoProperties;
import com.odontocare.whatsapp.dto.ChatQuery;
import com.odontocare.whatsapp.dto.WhatsAppContracts.ChatPage;
import com.odontocare.whatsapp.repository.ChatTimelineRepository;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ChatTimelineService {
  private final WhatsAppConversationService conversations;
  private final ChatTimelineRepository repository;
  private final KapsoProperties kapso;

  public ChatTimelineService(
      WhatsAppConversationService conversations,
      ChatTimelineRepository repository,
      KapsoProperties kapso) {
    this.conversations = conversations;
    this.repository = repository;
    this.kapso = kapso;
  }

  @Transactional(readOnly = true)
  public ChatPage messages(UUID id, ChatQuery query) {
    conversations.get(
        id); // Also checks that the conversation belongs to the active number/channel.
    return repository.messages(id, kapso.isEnabled(), query);
  }
}
