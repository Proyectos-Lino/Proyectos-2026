package com.odontocare.security.service;

import com.odontocare.audit.service.AuditService;
import com.odontocare.installation.service.ConfigurationLock;
import com.odontocare.security.dto.SetupRequest;
import com.odontocare.shared.web.ApiException;
import com.odontocare.users.model.UserAccount;
import com.odontocare.users.repository.*;
import java.util.HashSet;
import java.util.Set;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SetupService {
  private final UserAccountRepository users;
  private final RoleRepository roles;
  private final ConfigurationLock lock;
  private final PasswordEncoder encoder;
  private final AuditService audit;

  public SetupService(
      UserAccountRepository users,
      RoleRepository roles,
      ConfigurationLock lock,
      PasswordEncoder encoder,
      AuditService audit) {
    this.users = users;
    this.roles = roles;
    this.lock = lock;
    this.encoder = encoder;
    this.audit = audit;
  }

  @Transactional
  public void createAdministrator(SetupRequest request) {
    PasswordPolicy.requireValid(request.password());
    lock.acquire();
    if (users.count() != 0)
      throw ApiException.conflict(
          "La instalación ya tiene un administrador. Inicia sesión para gestionar usuarios.");
    var account = new UserAccount();
    account.setUsername(request.username());
    account.setDisplayName(request.displayName().strip());
    account.setPasswordHash(encoder.encode(request.password()));
    account.setRoles(new HashSet<>(Set.of(roles.findById("ADMIN").orElseThrow())));
    users.saveAndFlush(account);
    audit.recordAs(
        account.getId(),
        account.getDisplayName(),
        "SETUP_COMPLETED",
        "USER",
        account.getId(),
        "Creó la primera cuenta administradora.");
  }
}
