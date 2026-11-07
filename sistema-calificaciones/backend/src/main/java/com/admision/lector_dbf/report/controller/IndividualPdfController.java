package com.admision.lector_dbf.report.controller;

import com.admision.lector_dbf.publication.model.PublicationRequests.Lookup;
import com.admision.lector_dbf.report.service.IndividualPdfService;
import jakarta.validation.Valid;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestController
public class IndividualPdfController {
    private final IndividualPdfService service;
    public IndividualPdfController(IndividualPdfService service) { this.service=service; }
    @PostMapping("/api/v1/consulta-resultados/pdf")
    public ResponseEntity<byte[]> result(@Valid @RequestBody Lookup request) {
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_PDF).cacheControl(CacheControl.noStore())
                .header(HttpHeaders.CONTENT_DISPOSITION,"attachment; filename=constancia-resultado.pdf")
                .body(service.result(request.accessCode()));
    }
}
