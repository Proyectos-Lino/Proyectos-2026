package com.admision.lector_dbf.report.controller;

import com.admision.lector_dbf.report.service.PdfService;

import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/organizaciones/{organizationId}/evaluaciones/{evaluationId}/resultados.pdf")
public class PdfController {
    private final PdfService pdf;

    public PdfController(PdfService pdf) {
        this.pdf = pdf;
    }

    @GetMapping
    public ResponseEntity<byte[]> download(@PathVariable long organizationId, @PathVariable long evaluationId,
                                           Authentication authentication) {
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=resultado-" + evaluationId + ".pdf")
                .cacheControl(CacheControl.noStore()).body(pdf.results(organizationId, evaluationId, authentication));
    }
}
