package com.admision.lector_dbf.security;

import com.admision.lector_dbf.account.model.AccountModels;

import com.admision.lector_dbf.account.repository.AccountRepository;
import com.admision.lector_dbf.shared.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

import java.util.Arrays;

@Service
public class OrganizationAccess {
    private final AccountRepository accounts;

    public OrganizationAccess(AccountRepository accounts) { this.accounts = accounts; }

    public AccountModels.User current(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED", "Inicia sesión");
        }
        AccountModels.User user = accounts.findByEmail(authentication.getName())
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED", "Inicia sesión"));
        if (!user.active()) throw new ApiException(HttpStatus.FORBIDDEN, "ACCOUNT_DISABLED", "La cuenta está desactivada");
        return user;
    }

    public AccountModels.User require(long organizationId, Authentication authentication, String... roles) {
        AccountModels.User user = current(authentication);
        AccountModels.Membership membership = accounts.membership(organizationId, user.id())
                .orElseThrow(() -> new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "No tienes acceso a esta organización"));
        if (!membership.active() || (roles.length > 0 && Arrays.stream(roles).noneMatch(membership.role()::equals))) {
            throw new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "No tienes permiso para esta operación");
        }
        return user;
    }
}
