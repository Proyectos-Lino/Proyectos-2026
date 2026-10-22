package com.odontocare.clinical.repository;

import com.odontocare.clinical.model.Encounter;
import java.util.*;
import org.springframework.data.jpa.repository.*;

public interface EncounterRepository
    extends JpaRepository<Encounter, UUID>, JpaSpecificationExecutor<Encounter> {
  @Query("select e.patientId from Encounter e where e.id=:id")
  Optional<UUID> patientId(UUID id);

  boolean existsByAppointmentId(UUID appointmentId);

  Optional<Encounter> findByAppointmentId(UUID appointmentId);
}
