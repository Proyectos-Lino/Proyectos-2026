package com.odontocare.whatsapp.controller;

import com.odontocare.shared.pagination.*;
import com.odontocare.whatsapp.dto.ChatQuery;
import com.odontocare.whatsapp.dto.WhatsAppContracts.*;
import com.odontocare.whatsapp.model.WhatsAppConversation;
import com.odontocare.whatsapp.service.ChatTimelineService;
import com.odontocare.whatsapp.service.WhatsAppConversationService;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/whatsapp")
public class WhatsAppController {
  private final WhatsAppConversationService service;
  private final ChatTimelineService timeline;

  public WhatsAppController(WhatsAppConversationService service, ChatTimelineService timeline) {
    this.service = service;
    this.timeline = timeline;
  }

  @GetMapping("/connection")
  @PreAuthorize("hasAuthority('WHATSAPP_READ')")
  public Connection connection() {
    return service.connection();
  }

  @GetMapping("/conversations")
  @PreAuthorize("hasAuthority('WHATSAPP_READ')")
  public PageResponse<WhatsAppConversation> conversations(@Valid @ModelAttribute PageQuery query) {
    return service.list(query);
  }

  @GetMapping("/conversations/{id}")
  @PreAuthorize("hasAuthority('WHATSAPP_READ')")
  public WhatsAppConversation conversation(@PathVariable UUID id) {
    return service.get(id);
  }

  @GetMapping("/conversations/{id}/messages")
  @PreAuthorize("hasAuthority('WHATSAPP_READ')")
  public PageResponse<Message> messages(
      @PathVariable UUID id,
      @Valid @ModelAttribute PageQuery query,
      @RequestParam(required = false) String messageDirection,
      @RequestParam(required = false) String status) {
    return service.messages(id, query, messageDirection, status);
  }

  @PostMapping("/conversations/{id}/messages")
  @PreAuthorize("hasAuthority('WHATSAPP_WRITE')")
  public Message send(@PathVariable UUID id, @Valid @RequestBody SendRequest request) {
    return service.enqueue(id, request.body(), request.requestKey(), false);
  }

  @GetMapping("/conversations/{id}/timeline")
  @PreAuthorize("hasAuthority('WHATSAPP_READ')")
  public ChatPage timeline(@PathVariable UUID id, @Valid @ModelAttribute ChatQuery query) {
    return timeline.messages(id, query);
  }

  @PostMapping("/conversations/{id}/test-reply")
  @PreAuthorize("hasAuthority('WHATSAPP_WRITE')")
  public Message testReply(@PathVariable UUID id, @Valid @RequestBody TestReplyRequest request) {
    return service.enqueue(id, "", request.requestKey(), true);
  }
}
