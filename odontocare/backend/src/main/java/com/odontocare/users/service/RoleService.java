package com.odontocare.users.service;

import com.odontocare.audit.service.AuditService;
import com.odontocare.installation.service.ConfigurationLock;
import com.odontocare.security.model.Permission;
import com.odontocare.shared.pagination.*;
import com.odontocare.shared.web.ApiException;
import com.odontocare.users.dto.*;
import com.odontocare.users.repository.RoleRepository;
import java.util.*;
import java.util.stream.Collectors;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RoleService {
  private final RoleRepository roles;
  private final ConfigurationLock lock;
  private final AuditService audit;

  public RoleService(RoleRepository roles, ConfigurationLock lock, AuditService audit) {
    this.roles = roles;
    this.lock = lock;
    this.audit = audit;
  }

  @Transactional(readOnly = true)
  public PageResponse<RoleResponse> list(PageQuery query) {
    if (!Set.of("name", "code").contains(query.getSort()))
      throw ApiException.badRequest("El campo de ordenación no está permitido.");
    var page =
        PageRequest.of(
            query.getPage(),
            query.getSize(),
            Sort.by(Sort.Direction.fromString(query.getDirection()), query.getSort())
                .and(Sort.by("code")));
    return PageResponse.of(
        roles
            .findAll(SearchSpecifications.text(query.getSearch(), "name", "code"), page)
            .map(
                role ->
                    new RoleResponse(
                        role.getCode(),
                        role.getName(),
                        Set.copyOf(role.getPermissions()),
                        role.getVersion())));
  }

  @Transactional
  public RoleResponse update(String code, RoleRequest request) {
    lock.acquire();
    var role = roles.findById(code).orElseThrow(ApiException::notFound);
    role.checkVersion(request.version());
    Set<String> permissions =
        request.permissions().stream().map(Enum::name).collect(Collectors.toSet());
    Set<String> all =
        EnumSet.allOf(Permission.class).stream().map(Enum::name).collect(Collectors.toSet());
    if (code.equals("ADMIN") && !permissions.equals(all))
      throw ApiException.badRequest(
          "El administrador conserva los permisos necesarios para recuperar y gestionar la"
              + " instalación.");
    if (!code.equals("ADMIN") && permissions.contains("ROLES_WRITE"))
      throw ApiException.badRequest("Solo el administrador puede modificar la política de roles.");
    if (permissions.contains("REPORTS_EXPORT") && !permissions.contains("REPORTS_READ"))
      throw ApiException.badRequest("Exportar reportes requiere también consultar reportes.");
    for (String permission : permissions) {
      if (permission.endsWith("_WRITE")
          && !Set.of("PAYMENTS_WRITE", "EXPENSES_WRITE", "FINANCE_CONFIG_WRITE")
              .contains(permission)
          && !permissions.contains(permission.replace("_WRITE", "_READ"))) {
        throw ApiException.badRequest(
            "Para administrar una función, habilita también su permiso de consulta.");
      }
    }
    if (permissions.contains("DENTISTS_WRITE") && !permissions.contains("SERVICES_READ"))
      throw ApiException.badRequest(
          "Administrar odontólogos necesita consultar los servicios que se les asignan.");
    if (permissions.contains("SCHEDULES_WRITE") && !permissions.contains("DENTISTS_READ"))
      throw ApiException.badRequest("Administrar horarios necesita consultar los odontólogos.");
    if (permissions.contains("APPOINTMENTS_WRITE")
        && !permissions.containsAll(Set.of("PATIENTS_READ", "DENTISTS_READ", "SERVICES_READ")))
      throw ApiException.badRequest(
          "Administrar citas necesita consultar pacientes, odontólogos y servicios.");
    if (permissions.contains("CLINICAL_WRITE")
        && !permissions.containsAll(
            Set.of("PATIENTS_READ", "DENTISTS_READ", "SERVICES_READ", "APPOINTMENTS_READ")))
      throw ApiException.badRequest(
          "Registrar clínica requiere consultar pacientes, odontólogos, servicios y citas.");
    if (permissions.contains("DOCUMENTS_WRITE") && !permissions.contains("PATIENTS_READ"))
      throw ApiException.badRequest("Cargar documentos requiere consultar pacientes.");
    if ((permissions.contains("CLINICAL_READ") || permissions.contains("DOCUMENTS_READ"))
        && !permissions.contains("PATIENTS_READ"))
      throw ApiException.badRequest(
          "Consultar expedientes o documentos requiere consultar la ficha del paciente.");
    if (permissions.contains("PLANS_WRITE")
        && !permissions.containsAll(Set.of("PATIENTS_READ", "DENTISTS_READ", "SERVICES_READ")))
      throw ApiException.badRequest(
          "Gestionar planes requiere consultar pacientes, profesionales y servicios.");
    if (permissions.contains("FINANCES_ADJUST") && !permissions.contains("FINANCES_READ"))
      throw ApiException.badRequest("Ajustar cargos requiere consultar finanzas.");
    if ((permissions.contains("PLANS_READ") || permissions.contains("FINANCES_READ"))
        && !permissions.contains("PATIENTS_READ"))
      throw ApiException.badRequest("Consultar planes o deuda requiere consultar pacientes.");
    if (permissions.stream()
            .anyMatch(
                p -> Set.of("PAYMENTS_WRITE", "EXPENSES_WRITE", "FINANCE_CONFIG_WRITE").contains(p))
        && !permissions.contains("FINANCES_READ"))
      throw ApiException.badRequest("Gestionar cobros y egresos requiere consultar finanzas.");
    role.setName(request.name().strip());
    role.replacePermissions(permissions);
    roles.saveAndFlush(role);
    audit.record(
        "ROLE_UPDATED",
        "ROLE",
        code,
        "Actualizó nombre y permisos del rol " + code + ": " + new TreeSet<>(permissions));
    return new RoleResponse(
        code, role.getName(), Set.copyOf(role.getPermissions()), role.getVersion());
  }
}
