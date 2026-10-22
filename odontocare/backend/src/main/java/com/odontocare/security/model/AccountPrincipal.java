package com.odontocare.security.model;

import com.odontocare.users.model.UserAccount;
import java.util.*;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;

public class AccountPrincipal extends User {
  private final UUID id;
  private final String displayName;
  private final long authVersion;
  private final Set<String> roleCodes;
  private final Set<String> permissions;

  public AccountPrincipal(UserAccount account) {
    super(
        account.getUsername(),
        account.getPasswordHash(),
        account.getActive(),
        true,
        true,
        true,
        authorities(account));
    id = account.getId();
    displayName = account.getDisplayName();
    authVersion = account.getAuthVersion();
    roleCodes = new TreeSet<>();
    permissions = new TreeSet<>();
    account
        .getRoles()
        .forEach(
            role -> {
              roleCodes.add(role.getCode());
              permissions.addAll(role.getPermissions());
            });
  }

  private static List<SimpleGrantedAuthority> authorities(UserAccount account) {
    Set<String> names = new TreeSet<>();
    account
        .getRoles()
        .forEach(
            role -> {
              names.add("ROLE_" + role.getCode());
              names.addAll(role.getPermissions());
            });
    return names.stream().map(SimpleGrantedAuthority::new).toList();
  }

  public UUID getId() {
    return id;
  }

  public String getDisplayName() {
    return displayName;
  }

  public long getAuthVersion() {
    return authVersion;
  }

  public Set<String> getRoleCodes() {
    return Set.copyOf(roleCodes);
  }

  public Set<String> getPermissions() {
    return Set.copyOf(permissions);
  }
}
