package com.labcore.platform.catalog;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface TestMethodRepository
    extends JpaRepository<TestMethod, UUID>, JpaSpecificationExecutor<TestMethod> {
  boolean existsByTestDefinitionIdAndCodeIgnoreCaseAndIdNot(
      UUID testDefinitionId, String code, UUID id);

  boolean existsByTestDefinitionIdAndCodeIgnoreCase(UUID testDefinitionId, String code);

  Optional<TestMethod> findByIdAndTestDefinitionIdAndActiveTrue(UUID id, UUID testDefinitionId);

  List<TestMethod> findByTestDefinitionIdAndActiveTrueOrderByNameAsc(UUID testDefinitionId);
}
