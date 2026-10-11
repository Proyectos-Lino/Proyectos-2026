package com.labcore.platform.security;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

import com.labcore.platform.catalog.LaboratoryAreaManagementService;
import com.labcore.platform.common.exception.BusinessRuleViolationException;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class UserDirectoryServiceTest {

  @Mock private ApplicationUserRepository userRepository;
  @Mock private ApplicationRoleRepository roleRepository;
  @Mock private LaboratoryAreaManagementService areaService;
  @Mock private PasswordEncoder passwordEncoder;

  @Test
  void preventsDeactivatingTheLastActiveAdministrator() {
    ApplicationUser administrator = administrator();
    given(userRepository.findById(any(UUID.class))).willReturn(Optional.of(administrator));
    given(userRepository.countByActiveTrueAndRoles_Code(RoleCode.ADMIN)).willReturn(1L);

    UserDirectoryService service = service();

    assertThatThrownBy(() -> service.updateStatus(UUID.randomUUID(), false, "another@example.test"))
        .isInstanceOf(BusinessRuleViolationException.class)
        .hasMessage("Debe existir al menos un administrador activo en el sistema.");
  }

  @Test
  void preventsAnAdministratorFromDeactivatingTheirOwnAccount() {
    ApplicationUser administrator = administrator();
    given(userRepository.findById(any(UUID.class))).willReturn(Optional.of(administrator));

    UserDirectoryService service = service();

    assertThatThrownBy(() -> service.updateStatus(UUID.randomUUID(), false, "admin@example.test"))
        .isInstanceOf(BusinessRuleViolationException.class)
        .hasMessage("No puedes desactivar tu propia cuenta.");
  }

  private ApplicationUser administrator() {
    return new ApplicationUser(
        "Administrador",
        "admin@example.test",
        "hash",
        Set.of(new ApplicationRole(RoleCode.ADMIN, "Admin")));
  }

  private UserDirectoryService service() {
    return new UserDirectoryService(userRepository, roleRepository, areaService, passwordEncoder);
  }
}
