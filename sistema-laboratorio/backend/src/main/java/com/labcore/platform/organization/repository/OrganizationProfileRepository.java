package com.labcore.platform.organization;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrganizationProfileRepository extends JpaRepository<OrganizationProfile, UUID> {
  Optional<OrganizationProfile> findByInstallationKey(String installationKey);
}
