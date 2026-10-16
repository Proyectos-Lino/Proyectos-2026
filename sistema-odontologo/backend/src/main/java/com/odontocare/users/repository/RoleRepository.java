package com.odontocare.users.repository;

import com.odontocare.users.model.RoleDefinition;
import org.springframework.data.jpa.repository.*;

public interface RoleRepository
    extends JpaRepository<RoleDefinition, String>, JpaSpecificationExecutor<RoleDefinition> {}
