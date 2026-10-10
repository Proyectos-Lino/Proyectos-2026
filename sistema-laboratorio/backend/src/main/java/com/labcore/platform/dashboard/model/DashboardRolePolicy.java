package com.labcore.platform.dashboard;

import com.labcore.platform.security.ApplicationUser;
import com.labcore.platform.security.RoleCode;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
class DashboardRolePolicy {
  boolean canViewCommercial(ApplicationUser actor) {
    return hasAnyRole(actor, RoleCode.ADMIN, RoleCode.COMMERCIAL);
  }

  boolean canViewTechnical(ApplicationUser actor) {
    return hasAnyRole(
        actor, RoleCode.ADMIN, RoleCode.TECHNICAL_MANAGER, RoleCode.ANALYST, RoleCode.VALIDATOR);
  }

  boolean isRestrictedAnalyst(ApplicationUser actor) {
    return hasAnyRole(actor, RoleCode.ANALYST)
        && !hasAnyRole(actor, RoleCode.ADMIN, RoleCode.TECHNICAL_MANAGER, RoleCode.VALIDATOR);
  }

  boolean isPureAnalyst(ApplicationUser actor) {
    return isRestrictedAnalyst(actor) && !canViewCommercial(actor);
  }

  boolean canViewFullTechnicalAnalytics(ApplicationUser actor) {
    return hasAnyRole(actor, RoleCode.ADMIN, RoleCode.TECHNICAL_MANAGER, RoleCode.VALIDATOR);
  }

  boolean hasAnyRole(ApplicationUser actor, RoleCode... roles) {
    Set<RoleCode> allowed = Set.of(roles);
    return actor.getRoles().stream().anyMatch(role -> allowed.contains(role.getCode()));
  }
}
