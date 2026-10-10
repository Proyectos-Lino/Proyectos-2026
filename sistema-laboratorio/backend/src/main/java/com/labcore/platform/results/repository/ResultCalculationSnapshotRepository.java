package com.labcore.platform.results;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ResultCalculationSnapshotRepository
    extends JpaRepository<ResultCalculationSnapshot, UUID> {
  List<ResultCalculationSnapshot> findByResultEntryIdOrderByResultRevisionDescFieldKeyAsc(
      UUID resultEntryId);
}
