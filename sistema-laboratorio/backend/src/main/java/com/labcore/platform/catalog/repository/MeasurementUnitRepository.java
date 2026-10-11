package com.labcore.platform.catalog;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface MeasurementUnitRepository
    extends JpaRepository<MeasurementUnit, UUID>, JpaSpecificationExecutor<MeasurementUnit> {
  boolean existsByCodeIgnoreCase(String code);

  boolean existsByCodeIgnoreCaseAndIdNot(String code, UUID id);

  List<MeasurementUnit> findByActiveTrueOrderByNameAsc();
}
