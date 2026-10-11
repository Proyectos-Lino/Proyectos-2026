package com.labcore.platform.security;

import java.util.Locale;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class AuthService {

  private final AuthenticationManager authenticationManager;
  private final ApplicationUserRepository userRepository;
  private final JwtTokenService tokenService;

  public AuthService(
      AuthenticationManager authenticationManager,
      ApplicationUserRepository userRepository,
      JwtTokenService tokenService) {
    this.authenticationManager = authenticationManager;
    this.userRepository = userRepository;
    this.tokenService = tokenService;
  }

  public AuthDtos.TokenResponse login(AuthDtos.LoginRequest request) {
    String email = request.email().trim().toLowerCase(Locale.ROOT);
    try {
      authenticationManager.authenticate(
          UsernamePasswordAuthenticationToken.unauthenticated(email, request.password()));
    } catch (BadCredentialsException exception) {
      throw new InvalidCredentialsException();
    }
    ApplicationUser user =
        userRepository.findByEmailIgnoreCase(email).orElseThrow(InvalidCredentialsException::new);
    if (!user.isActive()) throw new InvalidCredentialsException();
    return tokenService.issue(user);
  }

  public AuthDtos.CurrentUserResponse currentUser(String email) {
    ApplicationUser user =
        userRepository.findByEmailIgnoreCase(email).orElseThrow(InvalidCredentialsException::new);
    return new AuthDtos.CurrentUserResponse(
        user.getId(),
        user.getFullName(),
        user.getEmail(),
        user.getRoles().stream().map(role -> role.getCode().name()).sorted().toList());
  }
}
