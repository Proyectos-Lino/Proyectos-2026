package com.labcore.platform.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;

class JwtTokenServiceTest {

  @Test
  void issuesSignedTokenWithUserIdentityAndRoles() {
    JwtProperties properties =
        new JwtProperties(
            "labcore-test", "a-secure-development-secret-with-more-than-32-chars", 30);
    SecurityConfig config = new SecurityConfig();
    JwtEncoder encoder = config.jwtEncoder(properties);
    JwtDecoder decoder = config.jwtDecoder(properties);
    ApplicationRole role = new ApplicationRole(RoleCode.ADMIN, "Administrador");
    ApplicationUser user =
        new ApplicationUser("Ana Analista", "ana@example.test", "hash", Set.of(role));

    AuthDtos.TokenResponse response = new JwtTokenService(encoder, properties).issue(user);
    Jwt token = decoder.decode(response.accessToken());

    assertThat(token.getSubject()).isEqualTo("ana@example.test");
    assertThat(token.getClaimAsStringList("roles")).containsExactly("ADMIN");
    assertThat(response.user().fullName()).isEqualTo("Ana Analista");
  }
}
