package com.labcore.platform.security;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

@Service
public class JwtTokenService {

  private final JwtEncoder encoder;
  private final JwtProperties properties;

  public JwtTokenService(JwtEncoder encoder, JwtProperties properties) {
    this.encoder = encoder;
    this.properties = properties;
  }

  public AuthDtos.TokenResponse issue(ApplicationUser user) {
    Instant now = Instant.now();
    Instant expiresAt = now.plus(properties.expirationMinutes(), ChronoUnit.MINUTES);
    List<String> roles =
        user.getRoles().stream().map(role -> role.getCode().name()).sorted().toList();
    JwtClaimsSet claims =
        JwtClaimsSet.builder()
            .issuer(properties.issuer())
            .issuedAt(now)
            .expiresAt(expiresAt)
            .subject(user.getEmail())
            .claim("userId", user.getId().toString())
            .claim("fullName", user.getFullName())
            .claim("roles", roles)
            .build();
    String token =
        encoder
            .encode(JwtEncoderParameters.from(JwsHeader.with(() -> "HS256").build(), claims))
            .getTokenValue();
    return new AuthDtos.TokenResponse(
        token,
        "Bearer",
        expiresAt,
        new AuthDtos.CurrentUserResponse(user.getId(), user.getFullName(), user.getEmail(), roles));
  }
}
