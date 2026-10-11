package com.labcore.platform.catalog;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface ResultTemplateRepository
    extends JpaRepository<ResultTemplate, UUID>, JpaSpecificationExecutor<ResultTemplate> {
  boolean existsByTestDefinitionIdAndSampleTypeIdAndNameIgnoreCase(
      UUID testDefinitionId, UUID sampleTypeId, String name);

  boolean existsByTestDefinitionIdAndSampleTypeIdIsNullAndNameIgnoreCase(
      UUID testDefinitionId, String name);

  boolean existsByTestDefinitionIdAndSampleTypeIdAndNameIgnoreCaseAndIdNot(
      UUID testDefinitionId, UUID sampleTypeId, String name, UUID id);

  boolean existsByTestDefinitionIdAndSampleTypeIdIsNullAndNameIgnoreCaseAndIdNot(
      UUID testDefinitionId, String name, UUID id);

  Optional<ResultTemplate>
      findFirstByTestDefinitionIdAndSampleTypeIdAndActiveTrueOrderByCreatedAtAsc(
          UUID testDefinitionId, UUID sampleTypeId);

  Optional<ResultTemplate>
      findFirstByTestDefinitionIdAndSampleTypeIdIsNullAndActiveTrueOrderByCreatedAtAsc(
          UUID testDefinitionId);
}
