package com.labcore.platform.reports;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface LaboratoryReportRepository
    extends JpaRepository<LaboratoryReport, UUID>, JpaSpecificationExecutor<LaboratoryReport> {
  Optional<LaboratoryReport> findFirstByServiceOrderIdOrderByReportVersionDesc(UUID serviceOrderId);

  boolean existsByServiceOrderIdAndStatus(UUID serviceOrderId, LaboratoryReportStatus status);

  List<LaboratoryReport> findByServiceOrderIdAndStatusIn(
      UUID serviceOrderId, Collection<LaboratoryReportStatus> statuses);

  Page<LaboratoryReport> findAllByOrderByCreatedAtDesc(Pageable pageable);

  long countByStatus(LaboratoryReportStatus status);
}
