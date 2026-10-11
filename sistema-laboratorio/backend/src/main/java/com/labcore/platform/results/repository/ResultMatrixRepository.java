package com.labcore.platform.results;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ResultMatrixRepository extends JpaRepository<ResultMatrix, UUID> {
  Optional<ResultMatrix> findByResultEntryId(UUID resultEntryId);
}
