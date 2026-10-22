package com.odontocare.users.service;

import com.odontocare.audit.service.AuditService;
import com.odontocare.dentists.repository.DentistRepository;
import com.odontocare.installation.service.ConfigurationLock;
import com.odontocare.security.model.AccountPrincipal;
import com.odontocare.security.service.PasswordPolicy;
import com.odontocare.shared.pagination.*;
import com.odontocare.shared.web.ApiException;
import com.odontocare.users.dto.*;
import com.odontocare.users.model.*;
import com.odontocare.users.repository.*;
import java.util.*;
import java.util.stream.Collectors;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService {
  private final UserAccountRepository users;
  private final RoleRepository roles;
  private final DentistRepository dentists;
  private final ConfigurationLock lock;
  private final PasswordEncoder encoder;
  private final AuditService audit;

  public UserService(
      UserAccountRepository users,
      RoleRepository roles,
      DentistRepository dentists,
      ConfigurationLock lock,
      PasswordEncoder encoder,
      AuditService audit) {
    this.users = users;
    this.roles = roles;
    this.dentists = dentists;
    this.lock = lock;
    this.encoder = encoder;
    this.audit = audit;
  }

  @Transactional(readOnly = true)
  public PageResponse<UserResponse> list(PageQuery query, Boolean active, RoleCode role) {
    Specification<UserAccount> spec =
        SearchSpecifications.<UserAccount>text(
                query.getSearch(), "username", "displayName", "email")
            .and(SearchSpecifications.equal("active", active));
    if (role != null)
      spec =
          spec.and(
              (root, q, cb) -> {
                q.distinct(true);
                return cb.equal(root.join("roles").get("code"), role.name());
              });
    return PageResponse.of(
        users
            .findAll(
                spec,
                query.pageable(
                    Map.of(
                        "name", "displayName", "username", "username", "createdAt", "createdAt")))
            .map(this::response));
  }

  @Transactional
  public UserResponse create(UserRequest request) {
    PasswordPolicy.requireValid(request.password());
    lock.acquire();
    checkAdministrativeAccess(null, request.roles());
    if (users.existsByUsername(request.username()))
      throw ApiException.conflict("El nombre de usuario ya está registrado.");
    var account = new UserAccount();
    apply(account, request);
    users.saveAndFlush(account);
    audit.record(
        "USER_CREATED",
        "USER",
        account.getId(),
        "Creó la cuenta " + account.getUsername() + ". Roles: " + roleCodes(account));
    return response(account);
  }

  @Transactional
  public UserResponse update(UUID id, UserRequest request) {
    lock.acquire();
    var account = users.findWithAccessById(id).orElseThrow(ApiException::notFound);
    account.checkVersion(request.version());
    checkAdministrativeAccess(account, request.roles());
    var actor = currentActor();
    if (actor.getId().equals(id) && !request.active())
      throw ApiException.badRequest("No puedes desactivar tu propia cuenta.");
    boolean remainsAdmin = request.active() && request.roles().contains(RoleCode.ADMIN);
    if (account.getActive()
        && roleCodes(account).contains("ADMIN")
        && !remainsAdmin
        && users.countActiveAdministrators() <= 1) {
      throw ApiException.conflict("Debe quedar al menos una cuenta administradora activa.");
    }
    if ((!request.active() || !request.roles().contains(RoleCode.DENTIST))
        && dentists.existsByUserIdAndActiveTrue(id)) {
      throw ApiException.conflict(
          "Desactiva primero al odontólogo asociado antes de retirar su acceso o su rol.");
    }
    if (users.existsByUsernameAndIdNot(request.username(), id))
      throw ApiException.conflict("El nombre de usuario ya está registrado.");
    boolean credentialsChanged =
        !account.getUsername().equals(request.username())
            || account.getActive() != request.active()
            || !roleCodes(account)
                .equals(request.roles().stream().map(Enum::name).collect(Collectors.toSet()))
            || request.password() != null && !request.password().isEmpty();
    apply(account, request);
    if (credentialsChanged) account.setAuthVersion(account.getAuthVersion() + 1);
    users.saveAndFlush(account);
    audit.record(
        "USER_UPDATED",
        "USER",
        id,
        "Actualizó la cuenta "
            + account.getUsername()
            + ". Estado: "
            + (account.getActive() ? "activo" : "inactivo")
            + ". Roles: "
            + roleCodes(account));
    if (request.password() != null && !request.password().isEmpty())
      audit.record(
          "PASSWORD_CHANGED", "USER", id, "Actualizó la contraseña sin registrar su contenido.");
    return response(account);
  }

  private void apply(UserAccount account, UserRequest request) {
    account.setUsername(request.username());
    account.setDisplayName(request.displayName().strip());
    account.setEmail(request.email().strip());
    account.setActive(request.active());
    account.setRoles(
        new HashSet<>(roles.findAllById(request.roles().stream().map(Enum::name).toList())));
    if (account.getRoles().size() != request.roles().size())
      throw ApiException.badRequest("Hay roles no válidos.");
    if (request.password() != null && !request.password().isEmpty()) {
      PasswordPolicy.requireValid(request.password());
      account.setPasswordHash(encoder.encode(request.password()));
    }
  }

  private void checkAdministrativeAccess(UserAccount target, Set<RoleCode> requested) {
    if (!currentActor().getRoleCodes().contains("ADMIN")
        && (requested.contains(RoleCode.ADMIN)
            || target != null && roleCodes(target).contains("ADMIN")))
      throw ApiException.forbidden();
  }

  private AccountPrincipal currentActor() {
    return (AccountPrincipal) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
  }

  private Set<String> roleCodes(UserAccount account) {
    return account.getRoles().stream()
        .map(RoleDefinition::getCode)
        .collect(Collectors.toCollection(TreeSet::new));
  }

  private UserResponse response(UserAccount account) {
    return new UserResponse(
        account.getId(),
        account.getUsername(),
        account.getDisplayName(),
        account.getEmail(),
        account.getActive(),
        roleCodes(account),
        account.getVersion());
  }
}
