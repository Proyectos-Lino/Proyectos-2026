package com.labcore.platform.audit;

import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class IdentityAccessAuditService {
  private final IdentityAccessLogRepository repository;

  public IdentityAccessAuditService(IdentityAccessLogRepository repository) {
    this.repository = repository;
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void recordReportAccess(
      UUID reportId, String reportCode, String action, String reason, String actor) {
    repository.save(new IdentityAccessLog("REPORT", reportId, reportCode, action, reason, actor));
  }
}
