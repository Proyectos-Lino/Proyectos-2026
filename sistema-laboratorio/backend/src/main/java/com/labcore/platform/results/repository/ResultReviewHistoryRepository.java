package com.labcore.platform.results;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ResultReviewHistoryRepository extends JpaRepository<ResultReviewHistory, UUID> {
  List<ResultReviewHistory> findByResultEntryIdOrderByPerformedAtDesc(UUID resultEntryId);
}
