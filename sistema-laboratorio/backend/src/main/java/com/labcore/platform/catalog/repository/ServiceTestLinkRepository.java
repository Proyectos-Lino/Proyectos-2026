package com.labcore.platform.catalog;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ServiceTestLinkRepository extends JpaRepository<ServiceTestLink, ServiceTestId> {
  boolean existsByIdServiceIdAndIdTestDefinitionId(UUID serviceId, UUID testDefinitionId);

  void deleteByIdServiceId(UUID serviceId);

  List<ServiceTestLink> findByIdServiceIdOrderByDisplayOrderAsc(UUID serviceId);
}
