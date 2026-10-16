package com.odontocare.dentists.repository;

import com.odontocare.dentists.model.Dentist;
import java.util.UUID;
import org.springframework.data.jpa.repository.*;

public interface DentistRepository
    extends JpaRepository<Dentist, UUID>, JpaSpecificationExecutor<Dentist> {
  boolean existsByUserIdAndIdNot(UUID userId, UUID id);

  boolean existsByUserId(UUID userId);

  boolean existsByUserIdAndActiveTrue(UUID userId);

  boolean existsByLicenseNumberIgnoreCaseAndIdNot(String licenseNumber, UUID id);

  boolean existsByLicenseNumberIgnoreCase(String licenseNumber);

  @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
  @Query("select d from Dentist d where d.id=:id")
  java.util.Optional<Dentist> lockById(UUID id);
}
