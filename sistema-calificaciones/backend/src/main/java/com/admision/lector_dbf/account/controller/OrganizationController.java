package com.admision.lector_dbf.account.controller;

import com.admision.lector_dbf.account.model.AccountModels.Organization;
import com.admision.lector_dbf.account.model.AccountRequests.CreateOrganization;
import com.admision.lector_dbf.account.model.AccountRequests.UpdateOrganization;
import com.admision.lector_dbf.account.service.OrganizationService;
import com.admision.lector_dbf.account.model.OrganizationProfile;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/organizaciones")
public class OrganizationController {
    private final OrganizationService service;

    public OrganizationController(OrganizationService service) { this.service = service; }

    @PostMapping
    public ResponseEntity<Organization> create(@Valid @RequestBody CreateOrganization request, Authentication auth) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(
                request.currentOrganizationId(), request.code(), request.name(),request.profile(), auth));
    }

    @GetMapping("/{organizationId}")
    public Organization get(@PathVariable long organizationId, Authentication auth) {
        return service.get(organizationId, auth);
    }

    @PatchMapping("/{organizationId}")
    public Organization update(@PathVariable long organizationId,
            @Valid @RequestBody UpdateOrganization request, Authentication auth) {
        return service.update(organizationId, request.name(),request.profile(), auth);
    }

    @GetMapping("/{organizationId}/perfil")
    public OrganizationProfile profile(@PathVariable long organizationId,Authentication auth) {
        return service.profile(organizationId,auth);
    }
}
