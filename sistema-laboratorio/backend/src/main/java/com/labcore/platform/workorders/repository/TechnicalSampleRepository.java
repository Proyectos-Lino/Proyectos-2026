package com.labcore.platform.workorders;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TechnicalSampleRepository extends JpaRepository<TechnicalSample, UUID> {
  Optional<TechnicalSample> findByServiceOrderSampleId(UUID serviceOrderSampleId);

  List<TechnicalSample> findByServiceOrderSampleIdIn(Collection<UUID> serviceOrderSampleIds);
}
