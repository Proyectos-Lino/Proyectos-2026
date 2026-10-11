package com.labcore.platform.catalog;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface LaboratoryServiceRepository
    extends JpaRepository<LaboratoryService, UUID>, JpaSpecificationExecutor<LaboratoryService> {
  boolean existsByCodeIgnoreCase(String code);

  boolean existsByCodeIgnoreCaseAndIdNot(String code, UUID id);

  List<LaboratoryService> findByActiveTrueOrderByNameAsc();
}
