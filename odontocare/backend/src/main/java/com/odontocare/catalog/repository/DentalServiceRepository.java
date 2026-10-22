package com.odontocare.catalog.repository;

import com.odontocare.catalog.model.DentalService;
import java.util.UUID;
import org.springframework.data.jpa.repository.*;

public interface DentalServiceRepository
    extends JpaRepository<DentalService, UUID>, JpaSpecificationExecutor<DentalService> {
  boolean existsByNameIgnoreCaseAndIdNot(String name, UUID id);

  boolean existsByNameIgnoreCase(String name);

  boolean existsByCategoryIdAndActiveTrue(UUID categoryId);
}
