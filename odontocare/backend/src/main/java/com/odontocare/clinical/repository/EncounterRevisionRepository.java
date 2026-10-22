package com.odontocare.clinical.repository;

import com.odontocare.clinical.model.EncounterRevision;
import java.util.*;
import org.springframework.data.jpa.repository.*;

public interface EncounterRevisionRepository
    extends JpaRepository<EncounterRevision, UUID>, JpaSpecificationExecutor<EncounterRevision> {
  Optional<EncounterRevision> findByEncounterIdAndNumber(UUID encounterId, Integer number);
}
