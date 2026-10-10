package com.labcore.platform.reports;

import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LaboratoryReportHistoryRepository
    extends JpaRepository<LaboratoryReportHistory, UUID> {
  Page<LaboratoryReportHistory> findByLaboratoryReportIdOrderByOccurredAtDesc(
      UUID laboratoryReportId, Pageable pageable);
}
