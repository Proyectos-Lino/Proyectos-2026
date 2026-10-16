package com.odontocare.security.dto;

import com.odontocare.security.model.AccountPrincipal;
import java.util.*;

public record SessionResponse(boolean setupRequired, CurrentUser user) {
  public record CurrentUser(
      UUID id, String username, String displayName, Set<String> roles, Set<String> permissions) {}

  public static SessionResponse of(boolean setupRequired, AccountPrincipal principal) {
    return new SessionResponse(
        setupRequired,
        principal == null
            ? null
            : new CurrentUser(
                principal.getId(),
                principal.getUsername(),
                principal.getDisplayName(),
                principal.getRoleCodes(),
                principal.getPermissions()));
  }
}
