package com.odontocare.security.service;

import com.odontocare.security.model.AccountPrincipal;
import com.odontocare.users.repository.UserAccountRepository;
import java.util.UUID;
import org.springframework.security.core.userdetails.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AccountAccessService implements UserDetailsService {
  private final UserAccountRepository repository;

  public AccountAccessService(UserAccountRepository repository) {
    this.repository = repository;
  }

  @Override
  @Transactional(readOnly = true)
  public UserDetails loadUserByUsername(String username) {
    return new AccountPrincipal(
        repository
            .findByUsername(username.strip().toLowerCase(java.util.Locale.ROOT))
            .orElseThrow(() -> new UsernameNotFoundException("Invalid credentials")));
  }

  @Transactional(readOnly = true)
  public AccountPrincipal refresh(UUID id) {
    var account = repository.findWithAccessById(id).orElse(null);
    if (account == null || !account.getActive()) return null;
    var principal = new AccountPrincipal(account);
    principal.eraseCredentials();
    return principal;
  }

  @Transactional(readOnly = true)
  public boolean setupRequired() {
    return repository.count() == 0;
  }
}
