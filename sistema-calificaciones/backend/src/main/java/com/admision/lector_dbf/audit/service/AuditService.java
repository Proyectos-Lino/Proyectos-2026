package com.admision.lector_dbf.audit.service;

import com.admision.lector_dbf.audit.model.AuditEvent;
import com.admision.lector_dbf.audit.repository.AuditRepository;
import com.admision.lector_dbf.security.OrganizationAccess;
import com.admision.lector_dbf.shared.PageRequest;
import com.admision.lector_dbf.shared.PageResult;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

@Service
public class AuditService {
    private final AuditRepository repository;
    private final OrganizationAccess access;
    public AuditService(AuditRepository repository, OrganizationAccess access) {
        this.repository = repository; this.access = access;
    }
    public PageResult<AuditEvent> page(long organizationId, long evaluationId, int page, int size, Authentication auth) {
        access.require(organizationId, auth);
        new PageRequest(page, size);
        return repository.page(organizationId, evaluationId, page, size);
    }
}
