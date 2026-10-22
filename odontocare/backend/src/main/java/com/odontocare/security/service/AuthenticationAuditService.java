package com.odontocare.security.service;

import com.odontocare.audit.service.AuditService;
import com.odontocare.security.model.AccountPrincipal;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthenticationAuditService {
  private final AuditService audit;

  public AuthenticationAuditService(AuditService audit) {
    this.audit = audit;
  }

  @Transactional
  public void login(AccountPrincipal actor) {
    audit.recordAs(
        actor.getId(), actor.getDisplayName(), "LOGIN", "USER", actor.getId(), "Inició sesión.");
  }

  @Transactional
  public void logout(AccountPrincipal actor) {
    audit.recordAs(
        actor.getId(), actor.getDisplayName(), "LOGOUT", "USER", actor.getId(), "Cerró sesión.");
  }

  @Transactional
  public void failure() {
    audit.recordAs(
        null, "Anónimo", "LOGIN_FAILED", "SECURITY", "login", "Intento de acceso no válido.");
  }
}
