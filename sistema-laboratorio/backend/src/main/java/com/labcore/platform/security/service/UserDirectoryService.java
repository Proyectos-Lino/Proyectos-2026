package com.labcore.platform.security;

import com.labcore.platform.catalog.LaboratoryArea;
import com.labcore.platform.catalog.LaboratoryAreaManagementService;
import com.labcore.platform.common.exception.BusinessRuleViolationException;
import com.labcore.platform.common.exception.ResourceNotFoundException;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class UserDirectoryService {
  private final ApplicationUserRepository userRepository;
  private final ApplicationRoleRepository roleRepository;
  private final LaboratoryAreaManagementService areaService;
  private final PasswordEncoder passwordEncoder;

  public UserDirectoryService(
      ApplicationUserRepository userRepository,
      ApplicationRoleRepository roleRepository,
      LaboratoryAreaManagementService areaService,
      PasswordEncoder passwordEncoder) {
    this.userRepository = userRepository;
    this.roleRepository = roleRepository;
    this.areaService = areaService;
    this.passwordEncoder = passwordEncoder;
  }

  public UserDtos.UserSummary create(UserDtos.CreateUserRequest request) {
    String email = request.email().trim().toLowerCase(Locale.ROOT);
    if (userRepository.existsByEmailIgnoreCase(email)) {
      throw new BusinessRuleViolationException("Ya existe un usuario con ese correo electrónico.");
    }
    LaboratoryArea area = getSelectedArea(request.laboratoryAreaId());
    Set<ApplicationRole> roles = resolveRoles(request.roles());
    ApplicationUser user =
        userRepository.save(
            new ApplicationUser(
                request.fullName().trim(),
                email,
                passwordEncoder.encode(request.password()),
                roles,
                area == null ? null : area.getId()));
    return toSummary(user);
  }

  @Transactional(readOnly = true)
  public Page<UserDtos.UserSummary> findPage(
      String search, RoleCode role, Boolean active, Pageable pageable) {
    Specification<ApplicationUser> specification = (root, query, builder) -> builder.conjunction();
    if (search != null && !search.isBlank()) {
      String value = "%" + search.trim().toLowerCase(Locale.ROOT) + "%";
      specification =
          specification.and(
              (root, query, builder) ->
                  builder.or(
                      builder.like(builder.lower(root.get("fullName").as(String.class)), value),
                      builder.like(builder.lower(root.get("email").as(String.class)), value)));
    }
    if (active != null) {
      specification =
          specification.and((root, query, builder) -> builder.equal(root.get("active"), active));
    }
    if (role != null) {
      specification =
          specification.and(
              (root, query, builder) -> {
                query.distinct(true);
                return builder.equal(root.join("roles").get("code"), role);
              });
    }
    return userRepository.findAll(specification, pageable).map(this::toSummary);
  }

  @Transactional(readOnly = true)
  public List<UserDtos.UserSummary> listAll() {
    return userRepository.findAll().stream()
        .map(this::toSummary)
        .sorted(
            java.util.Comparator.comparing(
                UserDtos.UserSummary::fullName, String.CASE_INSENSITIVE_ORDER))
        .toList();
  }

  @Transactional(readOnly = true)
  public List<UserDtos.UserSummary> analysts() {
    return listAll().stream()
        .filter(
            user ->
                user.active()
                    && (user.roles().contains(RoleCode.ANALYST)
                        || user.roles().contains(RoleCode.ADMIN)))
        .toList();
  }

  @Transactional(readOnly = true)
  public List<UserDtos.UserSummary> technicalManagers() {
    return listAll().stream()
        .filter(
            user ->
                user.active()
                    && (user.roles().contains(RoleCode.TECHNICAL_MANAGER)
                        || user.roles().contains(RoleCode.ADMIN)))
        .toList();
  }

  public UserDtos.UserSummary update(java.util.UUID userId, UserDtos.UpdateUserRequest request) {
    ApplicationUser user = findUser(userId);
    String email = request.email().trim().toLowerCase(Locale.ROOT);
    if (userRepository.existsByEmailIgnoreCaseAndIdNot(email, userId)) {
      throw new BusinessRuleViolationException("Ya existe un usuario con ese correo electrónico.");
    }
    Set<ApplicationRole> roles = resolveRoles(request.roles());
    ensureActiveAdministratorRemains(user, roles, user.isActive());
    LaboratoryArea area = getSelectedArea(request.laboratoryAreaId());
    user.update(request.fullName().trim(), email, roles, area == null ? null : area.getId());
    if (request.password() != null && !request.password().isBlank()) {
      user.updatePassword(passwordEncoder.encode(request.password()));
    }
    return toSummary(user);
  }

  public UserDtos.UserSummary updateStatus(
      java.util.UUID userId, boolean active, String currentEmail) {
    ApplicationUser user = findUser(userId);
    if (!active && user.getEmail().equalsIgnoreCase(currentEmail)) {
      throw new BusinessRuleViolationException("No puedes desactivar tu propia cuenta.");
    }
    ensureActiveAdministratorRemains(user, user.getRoles(), active);
    if (active) {
      user.activate();
    } else {
      user.deactivate();
    }
    return toSummary(user);
  }

  private ApplicationUser findUser(java.util.UUID userId) {
    return userRepository
        .findById(userId)
        .orElseThrow(() -> new ResourceNotFoundException("el usuario", userId));
  }

  private LaboratoryArea getSelectedArea(java.util.UUID areaId) {
    return areaId == null ? null : areaService.getActiveArea(areaId);
  }

  private Set<ApplicationRole> resolveRoles(Set<RoleCode> roles) {
    return roles.stream()
        .map(
            role ->
                roleRepository
                    .findByCode(role)
                    .orElseThrow(
                        () ->
                            new BusinessRuleViolationException(
                                "El rol " + role + " no está configurado.")))
        .collect(java.util.stream.Collectors.toSet());
  }

  private void ensureActiveAdministratorRemains(
      ApplicationUser user, Set<ApplicationRole> newRoles, boolean newActive) {
    boolean willRemainActiveAdmin =
        newActive && newRoles.stream().anyMatch(role -> role.getCode() == RoleCode.ADMIN);
    if (user.hasRole(RoleCode.ADMIN)
        && user.isActive()
        && !willRemainActiveAdmin
        && userRepository.countByActiveTrueAndRoles_Code(RoleCode.ADMIN) <= 1) {
      throw new BusinessRuleViolationException(
          "Debe existir al menos un administrador activo en el sistema.");
    }
  }

  private UserDtos.UserSummary toSummary(ApplicationUser user) {
    LaboratoryArea area =
        user.getLaboratoryAreaId() == null ? null : areaService.getArea(user.getLaboratoryAreaId());
    return new UserDtos.UserSummary(
        user.getId(),
        user.getFullName(),
        user.getEmail(),
        user.isActive(),
        user.getRoles().stream().map(ApplicationRole::getCode).sorted().toList(),
        user.getLaboratoryAreaId(),
        area == null ? null : area.getName());
  }
}
