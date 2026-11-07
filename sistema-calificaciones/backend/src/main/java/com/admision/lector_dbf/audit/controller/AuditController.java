package com.admision.lector_dbf.audit.controller;

import com.admision.lector_dbf.audit.model.AuditEvent;
import com.admision.lector_dbf.audit.service.AuditService;
import com.admision.lector_dbf.shared.PageResult;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/organizaciones/{organizationId}/evaluaciones/{evaluationId}/auditoria")
public class AuditController {
    private final AuditService service;
    public AuditController(AuditService service) { this.service = service; }
    @GetMapping("/pagina")
    public PageResult<AuditEvent> page(@PathVariable long organizationId, @PathVariable long evaluationId,
                                        @RequestParam(defaultValue="0") int page,
                                        @RequestParam(defaultValue="20") int size, Authentication auth) {
        return service.page(organizationId, evaluationId, page, size, auth);
    }
}
