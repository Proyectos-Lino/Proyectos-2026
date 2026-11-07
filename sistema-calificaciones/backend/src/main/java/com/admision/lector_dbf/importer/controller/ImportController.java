package com.admision.lector_dbf.importer.controller;

import com.admision.lector_dbf.importer.model.ImportModels;
import com.admision.lector_dbf.importer.model.DbfPreviewModels.*;
import com.admision.lector_dbf.importer.service.DbfPreviewService;
import com.admision.lector_dbf.importer.service.OmrImportService;
import com.admision.lector_dbf.shared.PageResult;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.math.BigDecimal;

@RestController
@RequestMapping("/api/v1/organizaciones/{organizationId}/evaluaciones/{evaluationId}/importaciones")
public class ImportController {
    private final OmrImportService service;
    private final DbfPreviewService previews;

    public ImportController(OmrImportService service, DbfPreviewService previews) {
        this.service = service;
        this.previews = previews;
    }

    @GetMapping
    public List<ImportModels.Batch> list(@PathVariable long organizationId, @PathVariable long evaluationId,
                                             Authentication auth) {
        return service.batches(organizationId, evaluationId, auth);
    }

    @GetMapping("/pagina")
    public PageResult<ImportModels.Batch> page(@PathVariable long organizationId, @PathVariable long evaluationId,
            @RequestParam(defaultValue = "") String query, @RequestParam(defaultValue = "") String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size, Authentication auth) {
        return service.batchesPage(organizationId, evaluationId, query, status, page, size, auth);
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ImportModels.ImportSummary> upload(@PathVariable long organizationId,
            @PathVariable long evaluationId, @RequestPart("identifi") MultipartFile identification,
            @RequestPart("respuest") MultipartFile responses, @RequestPart("claves") MultipartFile keys,
            Authentication auth) throws JsonProcessingException {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.upload(organizationId, evaluationId,
                identification, responses, keys, auth));
    }

    @PostMapping(value = "/preparar-y-cargar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ImportModels.ImportSummary> prepareAndUpload(@PathVariable long organizationId,
            @PathVariable long evaluationId, @RequestParam int questions, @RequestParam int options,
            @RequestParam BigDecimal correct, @RequestParam BigDecimal incorrect,
            @RequestParam BigDecimal blank, @RequestParam(required = false) BigDecimal passingScore,
            @RequestPart("identifi") MultipartFile identification,
            @RequestPart("respuest") MultipartFile responses, @RequestPart("claves") MultipartFile keys,
            Authentication auth) throws JsonProcessingException {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.prepareAndUpload(
                organizationId, evaluationId, questions, options, correct, incorrect, blank, passingScore,
                identification, responses, keys, auth));
    }

    @PostMapping(value = "/columnas", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Inspection inspect(@PathVariable long organizationId, @PathVariable long evaluationId,
            @RequestPart("identifi") MultipartFile identification,
            @RequestPart("respuest") MultipartFile responses, @RequestPart("claves") MultipartFile keys,
            Authentication auth) {
        return previews.inspect(organizationId, evaluationId, identification, responses, keys, auth);
    }

    @PostMapping(value = "/vista-previa", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Preview preview(@PathVariable long organizationId, @PathVariable long evaluationId,
            @RequestPart("identifi") MultipartFile identification,
            @RequestPart("respuest") MultipartFile responses, @RequestPart("claves") MultipartFile keys,
            @RequestPart("mapping") ColumnMapping mapping, @RequestPart("options") Options options,
            @RequestParam(defaultValue = "TODOS") String filter,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size,
            Authentication auth) {
        return previews.preview(organizationId, evaluationId, identification, responses, keys,
                mapping, options, filter, page, size, auth);
    }

    @PostMapping(value = "/confirmar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ImportModels.ImportSummary> confirm(@PathVariable long organizationId,
            @PathVariable long evaluationId, @RequestPart("identifi") MultipartFile identification,
            @RequestPart("respuest") MultipartFile responses, @RequestPart("claves") MultipartFile keys,
            @RequestPart("mapping") ColumnMapping mapping, @RequestPart("options") Options options,
            @RequestParam String fingerprint, Authentication auth) throws JsonProcessingException {
        return ResponseEntity.status(HttpStatus.CREATED).body(previews.confirm(organizationId, evaluationId,
                identification, responses, keys, mapping, options, fingerprint, auth));
    }
}
