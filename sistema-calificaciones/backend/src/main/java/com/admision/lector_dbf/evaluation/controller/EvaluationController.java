package com.admision.lector_dbf.evaluation.controller;

import com.admision.lector_dbf.evaluation.model.EvaluationModels.*;
import com.admision.lector_dbf.evaluation.model.EvaluationRequests.*;
import com.admision.lector_dbf.evaluation.service.EvaluationService;
import com.admision.lector_dbf.shared.PageResult;
import com.fasterxml.jackson.core.JsonProcessingException;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/organizaciones/{organizationId}")
public class EvaluationController {
    private final EvaluationService service;
    public EvaluationController(EvaluationService service) { this.service = service; }

    @GetMapping("/evaluaciones")
    public List<Evaluation> evaluations(@PathVariable long organizationId, Authentication auth) {
        return service.list(organizationId, auth);
    }

    @GetMapping("/evaluaciones/pagina")
    public PageResult<Overview> page(@PathVariable long organizationId,
            @RequestParam(defaultValue = "") String query, @RequestParam(defaultValue = "") String status,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "10") int size,
            Authentication auth) {
        return service.page(organizationId, query, status, page, size, auth);
    }

    @GetMapping("/estadisticas")
    public Statistics statistics(@PathVariable long organizationId, Authentication auth) {
        return service.statistics(organizationId, auth);
    }

    @GetMapping("/evaluaciones/{evaluationId}/resumen")
    public Overview overview(@PathVariable long organizationId, @PathVariable long evaluationId, Authentication auth) {
        return service.overview(organizationId, evaluationId, auth);
    }

    @PostMapping("/evaluaciones")
    public ResponseEntity<Evaluation> create(@PathVariable long organizationId,
            @Valid @RequestBody CreateEvaluation request, Authentication auth) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(organizationId, request, auth));
    }

    @GetMapping("/evaluaciones/{evaluationId}")
    public Evaluation evaluation(@PathVariable long organizationId, @PathVariable long evaluationId, Authentication auth) {
        return service.find(organizationId, evaluationId, auth);
    }

    @PatchMapping("/evaluaciones/{evaluationId}")
    public Evaluation update(@PathVariable long organizationId, @PathVariable long evaluationId,
            @Valid @RequestBody UpdateEvaluation request, Authentication auth) {
        return service.update(organizationId, evaluationId, request, auth);
    }

    @PatchMapping("/evaluaciones/{evaluationId}/estado")
    public Evaluation status(@PathVariable long organizationId, @PathVariable long evaluationId,
            @Valid @RequestBody ChangeStatus request, Authentication auth) {
        return service.status(organizationId, evaluationId, request, auth);
    }

    @GetMapping("/evaluaciones/{evaluationId}/grupos")
    public List<Group> groups(@PathVariable long organizationId, @PathVariable long evaluationId, Authentication auth) {
        return service.groups(organizationId, evaluationId, auth);
    }

    @PatchMapping("/evaluaciones/{evaluationId}/grupos/{groupId}")
    public ResponseEntity<Void> updateCapacity(@PathVariable long organizationId, @PathVariable long evaluationId,
            @PathVariable long groupId, @Valid @RequestBody UpdateCapacity request, Authentication auth) {
        service.updateCapacity(organizationId, evaluationId, groupId, request, auth);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/evaluaciones/{evaluationId}/grupos/{groupId}")
    public ResponseEntity<Void> updateGroup(@PathVariable long organizationId, @PathVariable long evaluationId,
            @PathVariable long groupId, @Valid @RequestBody UpdateGroup request, Authentication auth) {
        service.updateGroup(organizationId, evaluationId, groupId, request, auth);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/evaluaciones/{evaluationId}/grupos")
    public ResponseEntity<?> createGroup(@PathVariable long organizationId, @PathVariable long evaluationId,
            @Valid @RequestBody CreateGroup request, Authentication auth) {
        return created(service.createGroup(organizationId, evaluationId, request, auth));
    }

    @GetMapping("/evaluaciones/{evaluationId}/formularios")
    public List<Form> forms(@PathVariable long organizationId, @PathVariable long evaluationId, Authentication auth) {
        return service.forms(organizationId, evaluationId, auth);
    }

    @PostMapping("/evaluaciones/{evaluationId}/formularios")
    public ResponseEntity<?> createForm(@PathVariable long organizationId, @PathVariable long evaluationId,
            @Valid @RequestBody CreateForm request, Authentication auth) {
        return created(service.createForm(organizationId, evaluationId, request, auth));
    }

    @PutMapping("/evaluaciones/{evaluationId}/formularios/{formId}/clave")
    public ResponseEntity<?> saveKey(@PathVariable long organizationId, @PathVariable long evaluationId,
            @PathVariable long formId, @Valid @RequestBody SaveKey request, Authentication auth) throws JsonProcessingException {
        return created(service.saveKey(organizationId, evaluationId, formId, request, auth));
    }

    @GetMapping("/evaluaciones/{evaluationId}/formularios/{formId}/clave")
    public AnswerKey key(@PathVariable long organizationId, @PathVariable long evaluationId,
            @PathVariable long formId, Authentication auth) {
        return service.key(organizationId, evaluationId, formId, auth);
    }

    @PostMapping("/evaluaciones/{evaluationId}/reglas")
    public ResponseEntity<?> saveRule(@PathVariable long organizationId, @PathVariable long evaluationId,
            @Valid @RequestBody SaveRule request, Authentication auth) {
        return created(service.saveRule(organizationId, evaluationId, request, auth));
    }

    @PostMapping("/evaluaciones/{evaluationId}/reglas/vista-previa-nota")
    public Map<String, java.math.BigDecimal> previewOfficialScore(@PathVariable long organizationId,
            @PathVariable long evaluationId, @Valid @RequestBody PreviewOfficialScore request, Authentication auth) {
        return Map.of("officialScore", service.previewOfficialScore(organizationId, evaluationId,
                request.formula(), request.originalScore(), auth));
    }

    @GetMapping("/evaluaciones/{evaluationId}/reglas/actual")
    public Rule rule(@PathVariable long organizationId, @PathVariable long evaluationId, Authentication auth) {
        return service.rule(organizationId, evaluationId, auth);
    }

    @GetMapping("/participantes")
    public List<Participant> participants(@PathVariable long organizationId, Authentication auth) {
        return service.participants(organizationId, auth);
    }

    @GetMapping("/participantes/pagina")
    public PageResult<Participant> participantsPage(@PathVariable long organizationId,
            @RequestParam(defaultValue = "") String query, @RequestParam(defaultValue = "") String document,
            @RequestParam(defaultValue = "") String profile,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size, Authentication auth) {
        return service.participantsPage(organizationId, query, document, page, size,profile, auth);
    }

    @PatchMapping("/participantes/{participantId}")
    public ResponseEntity<Void> updateParticipant(@PathVariable long organizationId, @PathVariable long participantId,
            @Valid @RequestBody UpdateParticipant request, Authentication auth) {
        service.updateParticipant(organizationId, participantId, request, auth);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/evaluaciones/{evaluationId}/inscripciones/participantes/{participantId}")
    public RegistrationStatus registrationStatus(@PathVariable long organizationId,@PathVariable long evaluationId,@PathVariable long participantId,Authentication auth) {
        return service.registrationStatus(organizationId,evaluationId,participantId,auth);
    }

    @PostMapping("/participantes")
    public ResponseEntity<?> createParticipant(@PathVariable long organizationId,
            @Valid @RequestBody CreateParticipant request, Authentication auth) {
        return created(service.createParticipant(organizationId, request, auth));
    }

    @PostMapping("/evaluaciones/{evaluationId}/inscripciones")
    public ResponseEntity<?> register(@PathVariable long organizationId, @PathVariable long evaluationId,
            @Valid @RequestBody RegisterParticipant request, Authentication auth) {
        return created(service.register(organizationId, evaluationId, request, auth));
    }

    @GetMapping("/evaluaciones/{evaluationId}/inscripciones/pagina")
    public PageResult<Registration> registrationsPage(@PathVariable long organizationId,
            @PathVariable long evaluationId, @RequestParam(defaultValue = "") String query,
            @RequestParam(required = false) Long groupId, @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size, Authentication auth) {
        return service.registrationsPage(organizationId, evaluationId, query, groupId, page, size, auth);
    }

    @PatchMapping("/evaluaciones/{evaluationId}/inscripciones/{registrationId}")
    public ResponseEntity<Void> updateRegistration(@PathVariable long organizationId, @PathVariable long evaluationId,
            @PathVariable long registrationId, @Valid @RequestBody UpdateRegistration request, Authentication auth) {
        service.updateRegistration(organizationId, evaluationId, registrationId, request, auth);
        return ResponseEntity.noContent().build();
    }

    private ResponseEntity<?> created(long id) {
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("id", id));
    }
}
