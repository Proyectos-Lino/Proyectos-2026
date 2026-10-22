package com.odontocare.audit.service;

import com.odontocare.audit.model.AuditEvent;
import com.odontocare.audit.repository.AuditEventRepository;
import com.odontocare.security.model.AccountPrincipal;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

@Service
public class AuditService {
  private final AuditEventRepository repository;

  public AuditService(AuditEventRepository repository) {
    this.repository = repository;
  }

  @Transactional(propagation = Propagation.MANDATORY)
  public void record(String action, String entityType, Object entityId, String summary) {
    var authentication = SecurityContextHolder.getContext().getAuthentication();
    var actor =
        authentication != null
                && authentication.getPrincipal() instanceof AccountPrincipal principal
            ? principal
            : null;
    recordAs(
        actor == null ? null : actor.getId(),
        actor == null ? "Sistema" : actor.getDisplayName(),
        action,
        entityType,
        entityId,
        summary);
  }

  @Transactional(propagation = Propagation.MANDATORY)
  public void recordAs(
      UUID actorId,
      String actorName,
      String action,
      String entityType,
      Object entityId,
      String summary) {
    if (!AuditPolicy.essential(action)) return;
    repository.save(
        new AuditEvent(
            actorId,
            actorName,
            action,
            entityType,
            String.valueOf(entityId),
            summary,
            MDC.get("requestId")));
  }
}
