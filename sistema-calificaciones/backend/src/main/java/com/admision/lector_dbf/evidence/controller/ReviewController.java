package com.admision.lector_dbf.evidence.controller;

import com.admision.lector_dbf.evidence.model.ReviewModels;
import com.admision.lector_dbf.shared.PageResult;
import com.admision.lector_dbf.evidence.service.ReviewService;

import com.admision.lector_dbf.evidence.model.ReviewRequests.*;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/organizaciones/{organizationId}/evaluaciones/{evaluationId}")
public class ReviewController {
    private final ReviewService service;

    public ReviewController(ReviewService service) {
        this.service = service;
    }

    @GetMapping("/examenes/{sheetId}/revisiones")
    public List<ReviewModels.Review> list(@PathVariable long organizationId, @PathVariable long evaluationId,
            @PathVariable long sheetId, Authentication auth) {
        return service.list(organizationId, evaluationId, sheetId, auth);
    }

    @PostMapping("/examenes/{sheetId}/revisiones")
    public ResponseEntity<?> open(@PathVariable long organizationId, @PathVariable long evaluationId,
            @PathVariable long sheetId, @Valid @RequestBody OpenReview request, Authentication auth) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(Map.of("id", service.openForUser(organizationId, evaluationId, sheetId, request.reason(), auth)));
    }

    @PatchMapping("/revisiones/{reviewId}")
    public ResponseEntity<Void> resolve(@PathVariable long organizationId, @PathVariable long evaluationId,
            @PathVariable long reviewId, @Valid @RequestBody ResolveReview request, Authentication auth) {
        service.resolveForUser(organizationId, evaluationId, reviewId, request.decision(), request.explanation(), auth);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/revisiones/{reviewId}/evidencias")
    public List<ReviewModels.Evidence> evidence(@PathVariable long organizationId, @PathVariable long evaluationId,
            @PathVariable long reviewId, Authentication auth) {
        return service.evidence(organizationId, evaluationId, reviewId, auth);
    }

    @PostMapping(value = "/revisiones/{reviewId}/evidencias", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> attach(@PathVariable long organizationId, @PathVariable long evaluationId,
            @PathVariable long reviewId, @RequestPart("file") MultipartFile file, Authentication auth) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(Map.of("id", service.attachForUser(organizationId, evaluationId, reviewId, file, auth)));
    }

    @GetMapping("/evidencias/{evidenceId}/archivo")
    public ResponseEntity<byte[]> file(@PathVariable long organizationId, @PathVariable long evaluationId,
            @PathVariable long evidenceId, Authentication auth) {
        ReviewModels.EvidenceFile evidence = service.file(organizationId, evaluationId, evidenceId, auth);
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(evidence.metadata().contentType()))
                .cacheControl(CacheControl.noStore()).body(evidence.content());
    }

    @GetMapping("/revisiones/pagina")
    public PageResult<ReviewModels.ReviewCase> cases(@PathVariable long organizationId,
            @PathVariable long evaluationId, @RequestParam(defaultValue = "") String query,
            @RequestParam(defaultValue = "ALL") String assignment,
            @RequestParam(defaultValue = "") String status, @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size, Authentication auth) {
        return service.cases(organizationId, evaluationId, query, status, page, size,assignment, auth);
    }

    @GetMapping("/examenes/{sheetId}/revisiones/vecinos")
    public ReviewModels.Neighbors neighbors(@PathVariable long organizationId,@PathVariable long evaluationId,
            @PathVariable long sheetId,@RequestParam(defaultValue="ALL") String assignment,Authentication auth) {
        return service.neighbors(organizationId,evaluationId,sheetId,assignment,auth);
    }

    @GetMapping("/revisiones/responsables/pagina")
    public PageResult<ReviewModels.Assignee> assignees(@PathVariable long organizationId,
            @PathVariable long evaluationId, @RequestParam(defaultValue = "") String query,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "10") int size,
            Authentication auth) {
        return service.assignees(organizationId, evaluationId, query, page, size, auth);
    }

    @PatchMapping("/revisiones/{reviewId}/responsable")
    public ResponseEntity<Void> assign(@PathVariable long organizationId, @PathVariable long evaluationId,
            @PathVariable long reviewId, @RequestBody AssignReview request, Authentication auth) {
        service.assign(organizationId, evaluationId, reviewId, request.userId(), auth);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/examenes/{sheetId}/anulacion")
    public ResponseEntity<?> annul(@PathVariable long organizationId, @PathVariable long evaluationId,
            @PathVariable long sheetId, @Valid @RequestBody OpenReview request, Authentication auth) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(Map.of("reviewId", service.annul(organizationId, evaluationId, sheetId, request.reason(), auth)));
    }

    @PostMapping("/examenes/{sheetId}/reversion")
    public ResponseEntity<?> restore(@PathVariable long organizationId, @PathVariable long evaluationId,
            @PathVariable long sheetId, Authentication auth) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(Map.of("reviewId", service.restore(organizationId, evaluationId, sheetId, auth)));
    }

    @PatchMapping("/examenes/{sheetId}/motivo")
    public ResponseEntity<Void> updateReason(@PathVariable long organizationId, @PathVariable long evaluationId,
            @PathVariable long sheetId, @Valid @RequestBody OpenReview request, Authentication auth) {
        service.updateReason(organizationId, evaluationId, sheetId, request.reason(), auth);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/evidencias/{evidenceId}")
    public ResponseEntity<Void> deleteEvidence(@PathVariable long organizationId, @PathVariable long evaluationId,
            @PathVariable long evidenceId, Authentication auth) {
        service.deleteEvidence(organizationId, evaluationId, evidenceId, auth);
        return ResponseEntity.noContent().build();
    }
}
