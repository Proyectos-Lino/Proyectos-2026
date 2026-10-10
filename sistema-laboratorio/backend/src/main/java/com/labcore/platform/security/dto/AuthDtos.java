package com.labcore.platform.security;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class AuthDtos {
  private AuthDtos() {}

  public record LoginRequest(@NotBlank @Email String email, @NotBlank String password) {}

  public record CurrentUserResponse(UUID id, String fullName, String email, List<String> roles) {}

  public record TokenResponse(
      String accessToken, String tokenType, Instant expiresAt, CurrentUserResponse user) {}
}
