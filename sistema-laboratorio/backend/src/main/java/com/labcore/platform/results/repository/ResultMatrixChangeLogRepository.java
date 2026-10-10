package com.labcore.platform.results;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ResultMatrixChangeLogRepository
    extends JpaRepository<ResultMatrixChangeLog, UUID> {
  List<ResultMatrixChangeLog> findByResultMatrixIdOrderByChangedAtDesc(UUID matrixId);
}
