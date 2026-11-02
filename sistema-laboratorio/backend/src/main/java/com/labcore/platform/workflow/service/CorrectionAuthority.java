package com.labcore.platform.workflow;

import com.labcore.platform.security.*;
import com.labcore.platform.workorders.WorkOrder;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

@Component
public class CorrectionAuthority {
  private final ApplicationUserRepository users;

  public CorrectionAuthority(ApplicationUserRepository users) {
    this.users = users;
  }

  public ApplicationUser user(String email) {
    return users
        .findByEmailIgnoreCase(email)
        .filter(ApplicationUser::isActive)
        .orElseThrow(() -> new AccessDeniedException("Usuario no autorizado."));
  }

  public boolean hasRole(ApplicationUser user, RoleCode role) {
    return user.getRoles().stream().anyMatch(item -> item.getCode() == role);
  }

  public void requireManager(WorkOrder order, String email) {
    ApplicationUser user = user(email);
    if (!hasRole(user, RoleCode.ADMIN)
        && !(hasRole(user, RoleCode.TECHNICAL_MANAGER)
            && order.getResponsibleId().equals(user.getId()))) {
      throw new AccessDeniedException(
          "Solo el administrador o el responsable técnico asignado puede devolver esta operación.");
    }
  }
}
