package com.admision.lector_dbf.report.service;

import com.admision.lector_dbf.report.repository.ReportRepository;
import com.admision.lector_dbf.security.OrganizationAccess;
import org.springframework.stereotype.Service;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Isolation;

@Service
public class PdfService {
    private final ReportRepository reports;
    private final OrganizationAccess access;
    private final ResultsPdfRenderer renderer;

    public PdfService(ReportRepository reports,OrganizationAccess access,ResultsPdfRenderer renderer) {
        this.reports=reports; this.access=access; this.renderer=renderer;
    }

    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public byte[] results(long organizationId,long evaluationId,Authentication auth) {
        access.require(organizationId,auth);
        return renderer.render(reports.results(organizationId,evaluationId));
    }
}
