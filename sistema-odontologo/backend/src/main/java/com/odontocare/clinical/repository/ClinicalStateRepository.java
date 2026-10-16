package com.odontocare.clinical.repository;

import com.odontocare.clinical.model.ClinicalState;
import java.util.*;
import org.springframework.data.jpa.repository.*;

public interface ClinicalStateRepository
    extends JpaRepository<ClinicalState, UUID>, JpaSpecificationExecutor<ClinicalState> {
  Optional<ClinicalState> findFirstByPatientIdAndKindOrderBySequenceDesc(
      UUID patientId, String kind);
}
