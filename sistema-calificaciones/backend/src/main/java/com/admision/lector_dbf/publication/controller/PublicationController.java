package com.admision.lector_dbf.publication.controller;

import com.admision.lector_dbf.publication.model.PublicationModels.*;
import com.admision.lector_dbf.publication.model.PublicationRequests.*;
import com.admision.lector_dbf.publication.service.PublicationService;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
public class PublicationController {
    private final PublicationService service;
    public PublicationController(PublicationService service) { this.service = service; }

    @GetMapping("/api/v1/organizaciones/{organizationId}/evaluaciones/{evaluationId}/publicacion")
    public Policy policy(@PathVariable long organizationId, @PathVariable long evaluationId, Authentication auth) {
        return service.policy(organizationId, evaluationId, auth);
    }

    @PutMapping("/api/v1/organizaciones/{organizationId}/evaluaciones/{evaluationId}/publicacion")
    public Policy save(@PathVariable long organizationId, @PathVariable long evaluationId,
                       @Valid @RequestBody UpdatePolicy request, Authentication auth) {
        return service.save(organizationId, evaluationId, request, auth);
    }

    @PostMapping("/api/v1/organizaciones/{organizationId}/evaluaciones/{evaluationId}/publicacion/aprobacion")
    public Policy approve(@PathVariable long organizationId, @PathVariable long evaluationId, Authentication auth) {
        return service.approve(organizationId, evaluationId, auth);
    }

    @PostMapping("/api/v1/organizaciones/{organizationId}/evaluaciones/{evaluationId}/publicacion/confirmacion")
    public Policy publish(@PathVariable long organizationId, @PathVariable long evaluationId, Authentication auth) {
        return service.publish(organizationId, evaluationId, auth);
    }

    @PostMapping("/api/v1/organizaciones/{organizationId}/evaluaciones/{evaluationId}/publicacion/accesos")
    public ResponseEntity<byte[]> access(@PathVariable long organizationId, @PathVariable long evaluationId,
                                         Authentication auth) {
        byte[] content = service.exportAccess(organizationId, evaluationId, auth);
        return ResponseEntity.ok().contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
                .header("Content-Disposition", "attachment; filename=accesos-resultados.csv")
                .cacheControl(CacheControl.noStore()).body(content);
    }

    @PostMapping("/api/v1/consulta-resultados")
    public ResponseEntity<PublicResult> lookup(@Valid @RequestBody Lookup request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.lookup(request.accessCode()));
    }
}
