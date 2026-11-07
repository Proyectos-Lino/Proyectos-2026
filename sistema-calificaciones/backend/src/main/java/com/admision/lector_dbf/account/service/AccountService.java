package com.admision.lector_dbf.account.service;

import com.admision.lector_dbf.account.model.AccountModels;
import com.admision.lector_dbf.account.repository.AccountRepository;
import com.admision.lector_dbf.security.OrganizationAccess;

import com.admision.lector_dbf.shared.ApiException;
import com.admision.lector_dbf.shared.PageRequest;
import com.admision.lector_dbf.shared.PageResult;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;

@Service
public class AccountService {
    private final AccountRepository accounts;
    private final PasswordEncoder passwords;
    private final String bootstrapToken;
    private final OrganizationAccess access;

    public AccountService(AccountRepository accounts, PasswordEncoder passwords,
                          @Value("${app.bootstrap-token:}") String bootstrapToken, OrganizationAccess access) {
        this.accounts = accounts;
        this.passwords = passwords;
        this.bootstrapToken = bootstrapToken;
        this.access = access;
    }

    @Transactional
    public long bootstrap(String suppliedToken, String organizationCode, String organizationName,
                          String email, String name, String password) {
        if (bootstrapToken.isBlank()) throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "SETUP_DISABLED", "Configura BOOTSTRAP_TOKEN para crear la primera cuenta");
        byte[] expected = bootstrapToken.getBytes(StandardCharsets.UTF_8);
        byte[] supplied = suppliedToken == null ? new byte[0] : suppliedToken.getBytes(StandardCharsets.UTF_8);
        if (!MessageDigest.isEqual(expected, supplied)) throw new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "Clave de instalación inválida");
        accounts.lockBootstrap();
        if (accounts.userCount() != 0) throw ApiException.conflict("La instalación inicial ya se completó");
        long organizationId = accounts.createOrganization(organizationCode.trim(), organizationName.trim());
        long userId = accounts.createUser(email.trim().toLowerCase(), passwords.encode(password), name.trim());
        accounts.createMembership(organizationId, userId, "ADMINISTRADOR");
        return organizationId;
    }

    @Transactional
    public AccountModels.UserView createUser(long organizationId, String email, String name,
                                             String password, String role, Authentication auth) {
        access.require(organizationId, auth, "ADMINISTRADOR");
        validateRole(role);
        if (accounts.findByEmail(email).isPresent()) throw ApiException.conflict("Ya existe una cuenta con ese correo");
        long userId = accounts.createUser(email.trim().toLowerCase(), passwords.encode(password), name.trim());
        accounts.createMembership(organizationId, userId, role);
        return accounts.users(organizationId).stream().filter(user -> user.id() == userId).findFirst().orElseThrow();
    }

    public List<AccountModels.UserView> users(long organizationId, Authentication auth) {
        access.require(organizationId, auth, "ADMINISTRADOR");
        return accounts.users(organizationId);
    }

    @Transactional
    public AccountModels.UserView linkUser(long organizationId, String email, String role, Authentication auth) {
        access.require(organizationId, auth, "ADMINISTRADOR");
        validateRole(role);
        AccountModels.User user = accounts.findByEmail(email.trim())
                .orElseThrow(() -> ApiException.notFound("Cuenta con ese correo"));
        if (!user.active()) throw ApiException.conflict("La cuenta está desactivada");
        if (accounts.membership(organizationId, user.id()).isPresent())
            throw ApiException.conflict("La cuenta ya pertenece a esta organización");
        accounts.createMembership(organizationId, user.id(), role);
        return accounts.users(organizationId).stream().filter(item -> item.id() == user.id()).findFirst().orElseThrow();
    }

    public PageResult<AccountModels.UserView> usersPage(long organizationId, String query, String role,
                                                         int page, int size, Authentication auth) {
        access.require(organizationId, auth, "ADMINISTRADOR");
        new PageRequest(page, size);
        if (!role.isEmpty()) validateRole(role);
        return accounts.usersPage(organizationId, query, role, page, size);
    }

    @Transactional
    public void updateMembership(long organizationId, long userId, String role, boolean active, Authentication auth) {
        access.require(organizationId, auth, "ADMINISTRADOR");
        validateRole(role);
        AccountModels.Membership current = accounts.membership(organizationId, userId)
                .orElseThrow(() -> ApiException.notFound("Usuario"));
        if (current.active() && "ADMINISTRADOR".equals(current.role())
                && (!active || !"ADMINISTRADOR".equals(role)) && accounts.activeAdministrators(organizationId) <= 1) {
            throw ApiException.conflict("La organización debe conservar al menos un administrador activo");
        }
        if (accounts.updateMembership(organizationId, userId, role, active) == 0) throw ApiException.notFound("Usuario");
    }

    @Transactional
    public void updateUser(long organizationId, long userId, String name, String email,
                           String password, String role, boolean active, Authentication auth) {
        long actorId = access.require(organizationId, auth, "ADMINISTRADOR").id();
        validateRole(role);
        if (actorId != userId && accounts.sharedAccount(userId))
            throw ApiException.conflict("Esta cuenta pertenece a varias organizaciones. Solo puedes cambiar su rol y acceso en este espacio");
        AccountModels.Membership current = accounts.membership(organizationId, userId)
                .orElseThrow(() -> ApiException.notFound("Usuario"));
        if (current.active() && "ADMINISTRADOR".equals(current.role())
                && (!active || !"ADMINISTRADOR".equals(role)) && accounts.activeAdministrators(organizationId) <= 1) {
            throw ApiException.conflict("La organización debe conservar al menos un administrador activo");
        }
        String normalizedEmail = email.trim().toLowerCase();
        accounts.findByEmail(normalizedEmail).ifPresent(existing -> {
            if (existing.id() != userId) throw ApiException.conflict("Ya existe una cuenta con ese correo");
        });
        String hash = password == null || password.isBlank() ? null : passwords.encode(password);
        if (accounts.updateUser(userId, normalizedEmail, name.trim(), hash) == 0
                || accounts.updateMembership(organizationId, userId, role, active) == 0) {
            throw ApiException.notFound("Usuario");
        }
    }

    private void validateRole(String role) {
        if (!List.of("ADMINISTRADOR", "OPERADOR", "REVISOR", "LECTOR").contains(role)) {
            throw ApiException.badRequest("Rol inválido");
        }
    }
}
