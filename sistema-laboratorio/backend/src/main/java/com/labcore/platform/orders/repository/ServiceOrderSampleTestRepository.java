package com.labcore.platform.orders;

import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ServiceOrderSampleTestRepository
    extends JpaRepository<ServiceOrderSampleTest, UUID> {
  List<ServiceOrderSampleTest> findByServiceOrderSampleIdIn(Collection<UUID> sampleIds);

  List<ServiceOrderSampleTest> findByServiceOrderSampleId(UUID sampleId);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select test from ServiceOrderSampleTest test where test.id in :ids order by test.id")
  List<ServiceOrderSampleTest> findAllByIdForUpdate(@Param("ids") List<UUID> ids);
}
