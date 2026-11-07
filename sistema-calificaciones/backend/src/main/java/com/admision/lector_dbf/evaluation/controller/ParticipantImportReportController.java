package com.admision.lector_dbf.evaluation.controller;

import com.admision.lector_dbf.evaluation.service.ParticipantImportReportService;
import com.admision.lector_dbf.evaluation.model.ParticipantImportRow;
import com.admision.lector_dbf.shared.PageResult;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/organizaciones/{organization}/participantes/importaciones/{report}")
public class ParticipantImportReportController {
    private final ParticipantImportReportService service;
    public ParticipantImportReportController(ParticipantImportReportService service) { this.service=service; }
    @GetMapping("/pagina")
    public PageResult<ParticipantImportRow> page(@PathVariable long organization,@PathVariable long report,
            @RequestParam(defaultValue="") String query,@RequestParam(defaultValue="") String status,
            @RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size,Authentication auth) {
        return service.page(organization,report,query,status,page,size,auth);
    }
    @GetMapping("/resultado.csv")
    public ResponseEntity<byte[]> csv(@PathVariable long organization,@PathVariable long report,Authentication auth) {
        return ResponseEntity.ok().contentType(MediaType.parseMediaType("text/csv;charset=UTF-8"))
                .header(HttpHeaders.CONTENT_DISPOSITION,"attachment; filename=importacion-participantes.csv")
                .cacheControl(CacheControl.noStore()).body(service.csv(organization,report,auth));
    }
}
