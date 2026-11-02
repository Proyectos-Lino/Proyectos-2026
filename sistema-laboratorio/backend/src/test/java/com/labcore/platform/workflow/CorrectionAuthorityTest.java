package com.labcore.platform.workflow;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.labcore.platform.security.*;
import com.labcore.platform.workorders.WorkOrder;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

class CorrectionAuthorityTest {
  private final ApplicationUserRepository users = mock(ApplicationUserRepository.class);
  private final CorrectionAuthority authority = new CorrectionAuthority(users);

  private ApplicationUser user(RoleCode role) {
    var user =
        new ApplicationUser(
            "Usuario de prueba",
            "prueba@example.test",
            "hash",
            Set.of(new ApplicationRole(role, role.name())));
    when(users.findByEmailIgnoreCase(user.getEmail())).thenReturn(Optional.of(user));
    return user;
  }

  @Test
  void allowsOnlyAdministratorOrAssignedTechnicalManager() {
    var manager = user(RoleCode.TECHNICAL_MANAGER);
    var assigned =
        new WorkOrder("OT", UUID.randomUUID(), UUID.randomUUID(), manager.getId(), Instant.now());
    assertThatCode(() -> authority.requireManager(assigned, manager.getEmail()))
        .doesNotThrowAnyException();
    var another =
        new WorkOrder("OT", UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), Instant.now());
    assertThatThrownBy(() -> authority.requireManager(another, manager.getEmail()))
        .isInstanceOf(AccessDeniedException.class);
    var admin = user(RoleCode.ADMIN);
    assertThatCode(() -> authority.requireManager(another, admin.getEmail()))
        .doesNotThrowAnyException();
  }

  @Test
  void deniesCommercialAnalystAndValidatorEvenWhenAssignedAsResponsible() {
    for (RoleCode role :
        new RoleCode[] {RoleCode.COMMERCIAL, RoleCode.ANALYST, RoleCode.VALIDATOR}) {
      var user = user(role);
      var order =
          new WorkOrder("OT", UUID.randomUUID(), UUID.randomUUID(), user.getId(), Instant.now());
      assertThatThrownBy(() -> authority.requireManager(order, user.getEmail()))
          .isInstanceOf(AccessDeniedException.class);
    }
  }
}
