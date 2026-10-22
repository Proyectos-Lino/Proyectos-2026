package com.odontocare.audit.repository;

import com.odontocare.audit.model.AuditEvent;
import java.util.UUID;
import org.springframework.data.jpa.repository.*;

public interface AuditEventRepository
    extends JpaRepository<AuditEvent, UUID>, JpaSpecificationExecutor<AuditEvent> {}
