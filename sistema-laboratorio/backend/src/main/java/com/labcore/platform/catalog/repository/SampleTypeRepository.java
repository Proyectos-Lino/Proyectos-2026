package com.labcore.platform.catalog;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface SampleTypeRepository
    extends JpaRepository<SampleType, UUID>, JpaSpecificationExecutor<SampleType> {
  boolean existsByCodeIgnoreCase(String code);

  boolean existsByCodeIgnoreCaseAndIdNot(String code, UUID id);

  List<SampleType> findByActiveTrueOrderByNameAsc();
}
