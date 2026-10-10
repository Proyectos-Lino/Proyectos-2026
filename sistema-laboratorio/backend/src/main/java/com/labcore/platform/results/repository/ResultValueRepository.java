package com.labcore.platform.results;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ResultValueRepository extends JpaRepository<ResultValue, UUID> {
  List<ResultValue> findByResultEntryIdOrderByPositionAsc(UUID resultEntryId);
}
