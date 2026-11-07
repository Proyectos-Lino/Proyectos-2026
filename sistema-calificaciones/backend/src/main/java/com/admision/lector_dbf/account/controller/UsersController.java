package com.admision.lector_dbf.account.controller;

import com.admision.lector_dbf.account.model.AccountModels;
import com.admision.lector_dbf.account.service.AccountService;
import com.admision.lector_dbf.shared.PageResult;

import com.admision.lector_dbf.account.model.AccountRequests.*;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/organizaciones/{organizationId}/usuarios")
public class UsersController {
    private final AccountService service;
    public UsersController(AccountService service) {
        this.service = service;
    }

    @GetMapping
    public List<AccountModels.UserView> list(@PathVariable long organizationId, Authentication authentication) {
        return service.users(organizationId, authentication);
    }

    @GetMapping("/pagina")
    public PageResult<AccountModels.UserView> page(@PathVariable long organizationId,
            @RequestParam(defaultValue = "") String query, @RequestParam(defaultValue = "") String role,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "10") int size,
            Authentication authentication) {
        return service.usersPage(organizationId, query, role, page, size, authentication);
    }

    @PostMapping
    public ResponseEntity<AccountModels.UserView> create(@PathVariable long organizationId,
                                                              @Valid @RequestBody CreateUser request,
                                                              Authentication authentication) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.createUser(organizationId,
                request.email(), request.name(), request.password(), request.role(), authentication));
    }

    @PatchMapping("/{userId}")
    public ResponseEntity<Void> update(@PathVariable long organizationId, @PathVariable long userId,
                                       @Valid @RequestBody UpdateMembership request, Authentication authentication) {
        service.updateMembership(organizationId, userId, request.role(), request.active(), authentication);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/vincular")
    public ResponseEntity<AccountModels.UserView> link(@PathVariable long organizationId,
            @Valid @RequestBody LinkUser request, Authentication authentication) {
        return ResponseEntity.status(HttpStatus.CREATED).body(
                service.linkUser(organizationId, request.email(), request.role(), authentication));
    }

    @PatchMapping("/{userId}/perfil")
    public ResponseEntity<Void> updateUser(@PathVariable long organizationId, @PathVariable long userId,
                                           @Valid @RequestBody UpdateUser request, Authentication authentication) {
        service.updateUser(organizationId, userId, request.name(), request.email(), request.password(),
                request.role(), request.active(), authentication);
        return ResponseEntity.noContent().build();
    }
}
