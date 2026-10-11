package com.labcore.platform.security;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ApplicationRoleRepository extends JpaRepository<ApplicationRole, UUID> {
  Optional<ApplicationRole> findByCode(RoleCode code);
}
