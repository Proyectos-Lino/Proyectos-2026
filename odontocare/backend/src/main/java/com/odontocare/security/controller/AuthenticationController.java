package com.odontocare.security.controller;

import com.odontocare.security.dto.*;
import com.odontocare.security.model.AccountPrincipal;
import com.odontocare.security.service.*;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthenticationController {
  private final AccountAccessService access;
  private final SetupService setup;

  public AuthenticationController(AccountAccessService access, SetupService setup) {
    this.access = access;
    this.setup = setup;
  }

  @GetMapping("/session")
  public SessionResponse session(Authentication authentication) {
    var principal =
        authentication != null && authentication.getPrincipal() instanceof AccountPrincipal current
            ? current
            : null;
    return SessionResponse.of(access.setupRequired(), principal);
  }

  @GetMapping("/csrf")
  public CsrfResponse csrf(CsrfToken token) {
    return new CsrfResponse(token.getHeaderName(), token.getToken());
  }

  @PostMapping("/setup")
  @ResponseStatus(HttpStatus.CREATED)
  public void setup(@Valid @RequestBody SetupRequest request) {
    setup.createAdministrator(request);
  }

  public record CsrfResponse(String headerName, String token) {}
}
