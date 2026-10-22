package com.odontocare.catalog.repository;

import com.odontocare.catalog.model.ServiceCategory;
import java.util.UUID;
import org.springframework.data.jpa.repository.*;

public interface ServiceCategoryRepository
    extends JpaRepository<ServiceCategory, UUID>, JpaSpecificationExecutor<ServiceCategory> {
  boolean existsByNameIgnoreCaseAndIdNot(String name, UUID id);

  boolean existsByNameIgnoreCase(String name);
}
