package com.odontocare.clinical.repository;

import com.odontocare.clinical.model.ClinicalTemplate;
import java.util.*;
import org.springframework.data.jpa.repository.*;

public interface ClinicalTemplateRepository
    extends JpaRepository<ClinicalTemplate, UUID>, JpaSpecificationExecutor<ClinicalTemplate> {
  boolean existsByNameIgnoreCaseAndIdNot(String name, UUID id);

  boolean existsByNameIgnoreCase(String name);
}
