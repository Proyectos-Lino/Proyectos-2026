package com.labcore.platform.security;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface ApplicationUserRepository
    extends JpaRepository<ApplicationUser, UUID>, JpaSpecificationExecutor<ApplicationUser> {
  Optional<ApplicationUser> findByEmailIgnoreCase(String email);

  boolean existsByEmailIgnoreCase(String email);

  boolean existsByEmailIgnoreCaseAndIdNot(String email, UUID id);

  long countByActiveTrueAndRoles_Code(RoleCode role);

  long countByActiveTrue();
}
