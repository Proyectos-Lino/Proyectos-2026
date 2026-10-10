package com.labcore.platform.catalog;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface LaboratoryAreaRepository
    extends JpaRepository<LaboratoryArea, UUID>, JpaSpecificationExecutor<LaboratoryArea> {
  boolean existsByCodeIgnoreCase(String code);

  boolean existsByCodeIgnoreCaseAndIdNot(String code, UUID id);

  List<LaboratoryArea> findByActiveTrueOrderByNameAsc();
}
