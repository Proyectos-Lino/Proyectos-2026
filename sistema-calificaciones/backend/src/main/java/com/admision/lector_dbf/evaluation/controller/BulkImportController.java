package com.admision.lector_dbf.evaluation.controller;

import com.admision.lector_dbf.evaluation.model.BulkImportSummary;
import com.admision.lector_dbf.evaluation.service.BulkImportService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/organizaciones/{organizationId}")
public class BulkImportController {
    private final BulkImportService service;

    public BulkImportController(BulkImportService service) { this.service = service; }

    @PostMapping("/participantes/importar")
    public BulkImportSummary participants(@PathVariable long organizationId, @RequestParam MultipartFile file,
            Authentication auth) {
        return service.participants(organizationId, file, auth);
    }

    @PostMapping("/evaluaciones/{evaluationId}/grupos/importar")
    public BulkImportSummary groups(@PathVariable long organizationId, @PathVariable long evaluationId,
            @RequestParam MultipartFile file, Authentication auth) {
        return service.groups(organizationId, evaluationId, file, auth);
    }
}
