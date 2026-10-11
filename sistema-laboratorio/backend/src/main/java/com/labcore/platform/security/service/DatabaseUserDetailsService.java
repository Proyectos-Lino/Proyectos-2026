package com.labcore.platform.security;

import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
public class DatabaseUserDetailsService implements UserDetailsService {

  private final ApplicationUserRepository repository;

  public DatabaseUserDetailsService(ApplicationUserRepository repository) {
    this.repository = repository;
  }

  @Override
  public UserDetails loadUserByUsername(String email) {
    ApplicationUser user =
        repository
            .findByEmailIgnoreCase(email)
            .orElseThrow(() -> new UsernameNotFoundException("Usuario no encontrado."));
    return User.withUsername(user.getEmail())
        .password(user.getPasswordHash())
        .disabled(!user.isActive())
        .authorities(
            user.getRoles().stream()
                .map(role -> "ROLE_" + role.getCode().name())
                .toArray(String[]::new))
        .build();
  }
}
