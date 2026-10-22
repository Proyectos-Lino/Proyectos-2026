package com.odontocare.installation.repository;

import com.odontocare.installation.model.InstallationProfile;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.*;

public interface InstallationProfileRepository extends JpaRepository<InstallationProfile, Short> {
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select p from InstallationProfile p where p.id = 1")
  Optional<InstallationProfile> lockInstallation();

  @Lock(LockModeType.PESSIMISTIC_READ)
  @Query("select p from InstallationProfile p where p.id = 1")
  Optional<InstallationProfile> readLockedInstallation();
}
