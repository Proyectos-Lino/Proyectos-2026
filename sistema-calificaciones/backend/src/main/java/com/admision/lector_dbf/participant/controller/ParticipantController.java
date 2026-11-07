package com.admision.lector_dbf.participant.controller;

import com.admision.lector_dbf.participant.model.ParticipantModels.*;
import com.admision.lector_dbf.participant.model.ParticipantRequests.SaveEnrollment;
import com.admision.lector_dbf.participant.service.ParticipantService;
import com.admision.lector_dbf.shared.PageResult;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/organizaciones/{org}")
public class ParticipantController {
    private final ParticipantService service;
    public ParticipantController(ParticipantService service) { this.service = service; }

    @GetMapping("/personas/pagina")
    public PageResult<Person> people(@PathVariable long org, @RequestParam(defaultValue="") String query,
            @RequestParam(defaultValue="0") int page, @RequestParam(defaultValue="10") int size, Authentication auth) {
        return service.people(org, query, page, size, auth);
    }
    @GetMapping("/evaluaciones/{evaluation}/participantes/pagina")
    public PageResult<Enrollment> page(@PathVariable long org, @PathVariable long evaluation,
            @RequestParam(defaultValue="") String query, @RequestParam(required=false) Long groupId,
            @RequestParam(defaultValue="") String profile, @RequestParam(defaultValue="") String document,
            @RequestParam(defaultValue="0") int page, @RequestParam(defaultValue="10") int size, Authentication auth) {
        return service.page(org, evaluation, query, groupId, profile, document, page, size, auth);
    }
    @GetMapping("/evaluaciones/{evaluation}/participantes/resumen")
    public Summary summary(@PathVariable long org, @PathVariable long evaluation, Authentication auth) { return service.summary(org, evaluation, auth); }
    @PostMapping("/evaluaciones/{evaluation}/participantes")
    @ResponseStatus(HttpStatus.CREATED)
    public Enrollment create(@PathVariable long org, @PathVariable long evaluation, @Valid @RequestBody SaveEnrollment request, Authentication auth) {
        return service.create(org, evaluation, request, auth);
    }
    @PatchMapping("/evaluaciones/{evaluation}/participantes/{registration}")
    public Enrollment update(@PathVariable long org, @PathVariable long evaluation, @PathVariable long registration,
            @Valid @RequestBody SaveEnrollment request, Authentication auth) {
        return service.update(org, evaluation, registration, request, auth);
    }
}
