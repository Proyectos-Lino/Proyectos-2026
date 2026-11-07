package com.admision.lector_dbf.participant.controller;

import com.admision.lector_dbf.participant.model.ParticipantModels.*;
import com.admision.lector_dbf.participant.service.EnrollmentImportService;
import com.admision.lector_dbf.shared.PageResult;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/organizaciones/{org}/evaluaciones/{evaluation}/participantes")
public class EnrollmentImportController {
    private final EnrollmentImportService service;
    public EnrollmentImportController(EnrollmentImportService service) { this.service = service; }
    @GetMapping("/plantilla.csv")
    public ResponseEntity<byte[]> template(@PathVariable long org, @PathVariable long evaluation,
            @RequestParam(defaultValue="REGISTROS") String mode, Authentication auth) {
        return csv(service.template(org, evaluation, mode, auth), "plantilla-participantes.csv");
    }
    @PostMapping("/importaciones/vista-previa")
    public Preview preview(@PathVariable long org, @PathVariable long evaluation, @RequestParam MultipartFile file,
            @RequestParam(defaultValue="REGISTROS") String mode, @RequestParam(defaultValue="") String query,
            @RequestParam(defaultValue="") String action, @RequestParam(defaultValue="0") int page,
            @RequestParam(defaultValue="20") int size, Authentication auth) {
        return service.preview(org, evaluation, file, mode, query, action, page, size, auth);
    }
    @PostMapping("/importaciones/confirmar")
    public Imported confirm(@PathVariable long org, @PathVariable long evaluation, @RequestParam MultipartFile file,
            @RequestParam(defaultValue="REGISTROS") String mode, @RequestParam String fingerprint, Authentication auth) {
        return service.confirm(org, evaluation, file, mode, fingerprint, auth);
    }
    @GetMapping("/importaciones/{report}/pagina")
    public PageResult<ImportRow> report(@PathVariable long org, @PathVariable long evaluation, @PathVariable long report,
            @RequestParam(defaultValue="") String query, @RequestParam(defaultValue="") String action,
            @RequestParam(defaultValue="0") int page, @RequestParam(defaultValue="20") int size, Authentication auth) {
        return service.report(org, evaluation, report, query, action, page, size, auth);
    }
    @GetMapping("/importaciones/{report}/resultado.csv")
    public ResponseEntity<byte[]> reportCsv(@PathVariable long org, @PathVariable long evaluation, @PathVariable long report, Authentication auth) {
        return csv(service.reportCsv(org, evaluation, report, auth), "resultado-inscripciones.csv");
    }
    private ResponseEntity<byte[]> csv(byte[] content, String filename) {
        return ResponseEntity.ok().contentType(MediaType.parseMediaType("text/csv;charset=UTF-8"))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + filename)
                .cacheControl(CacheControl.noStore()).body(content);
    }
}
