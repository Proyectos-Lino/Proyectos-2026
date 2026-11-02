package com.labcore.platform.results;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ResultValidationRepository extends JpaRepository<ResultValidation, UUID> {
  Optional<ResultValidation> findFirstByResultEntryIdOrderByValidatedAtDesc(UUID resultEntryId);

  default Optional<ResultValidation> findByResultEntryId(UUID resultEntryId) {
    return findFirstByResultEntryIdOrderByValidatedAtDesc(resultEntryId);
  }
}
