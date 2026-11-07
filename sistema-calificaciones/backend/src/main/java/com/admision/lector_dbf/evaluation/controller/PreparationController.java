package com.admision.lector_dbf.evaluation.controller;

import com.admision.lector_dbf.evaluation.model.Preparation;
import com.admision.lector_dbf.evaluation.service.PreparationService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
public class PreparationController {
    private final PreparationService service;
    public PreparationController(PreparationService service) { this.service=service; }
    @GetMapping("/api/v1/organizaciones/{organization}/evaluaciones/{evaluation}/preparacion")
    public Preparation get(@PathVariable long organization,@PathVariable long evaluation,Authentication auth) {
        return service.get(organization,evaluation,auth);
    }
}
