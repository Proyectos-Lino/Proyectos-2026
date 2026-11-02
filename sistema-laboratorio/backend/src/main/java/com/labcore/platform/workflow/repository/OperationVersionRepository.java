package com.labcore.platform.workflow;

import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OperationVersionRepository extends JpaRepository<OperationVersion, UUID> {
  @Query(
      "select coalesce(max(v.revision), 0) from OperationVersion v where v.operationType = :type and v.operationId = :id")
  int lastRevision(@Param("type") OperationType type, @Param("id") UUID id);

  Page<OperationVersion> findByOperationTypeAndOperationIdOrderByRevisionDesc(
      OperationType type, UUID id, Pageable pageable);
}
