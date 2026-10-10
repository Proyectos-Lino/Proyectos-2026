package com.labcore.platform.organization;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrganizationProfileHistoryRepository
    extends JpaRepository<OrganizationProfileHistory, UUID> {}
