package com.labcore.platform.orders;

import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ServiceOrderSampleRepository extends JpaRepository<ServiceOrderSample, UUID> {
  List<ServiceOrderSample> findByServiceOrderIdOrderByCommercialSequenceAsc(UUID serviceOrderId);

  List<ServiceOrderSample> findByServiceOrderIdAndActiveTrueOrderByCommercialSequenceAsc(
      UUID serviceOrderId);

  List<ServiceOrderSample> findByIdIn(Collection<UUID> ids);

  Page<ServiceOrderSample> findByProducerIdOrderByCreatedAtDesc(UUID producerId, Pageable pageable);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select sample from ServiceOrderSample sample where sample.id in :ids order by sample.id")
  List<ServiceOrderSample> findAllByIdForUpdate(@Param("ids") List<UUID> ids);
}
