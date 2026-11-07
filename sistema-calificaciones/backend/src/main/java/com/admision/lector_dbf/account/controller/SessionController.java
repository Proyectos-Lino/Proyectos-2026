package com.admision.lector_dbf.account.controller;

import com.admision.lector_dbf.account.service.SessionService;
import com.admision.lector_dbf.account.model.AccountRequests.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
public class SessionController {
    private final SessionService service;

    public SessionController(SessionService service) {
        this.service = service;
    }

    @GetMapping("/session/csrf")
    public Object csrf(CsrfToken token) { return java.util.Map.of("headerName", token.getHeaderName(), "token", token.getToken()); }

    @PostMapping("/bootstrap")
    public ResponseEntity<?> bootstrap(@Valid @RequestBody BootstrapRequest request) {
        long id = service.bootstrap(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(java.util.Map.of("organizationId", id));
    }

    @PostMapping("/session/login")
    public SessionView login(@Valid @RequestBody LoginRequest request, HttpServletRequest servletRequest,
                             HttpServletResponse servletResponse) {
        return service.login(request, servletRequest, servletResponse);
    }

    @GetMapping("/session/me")
    public SessionView me(Authentication authentication) { return service.session(authentication); }

    @PostMapping("/session/logout")
    public ResponseEntity<Void> logout(HttpServletRequest request) {
        service.logout(request);
        return ResponseEntity.noContent().build();
    }
}
