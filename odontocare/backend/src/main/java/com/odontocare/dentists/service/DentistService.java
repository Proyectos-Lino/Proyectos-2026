package com.odontocare.dentists.service;

import com.odontocare.audit.service.AuditService;
import com.odontocare.catalog.model.DentalService;
import com.odontocare.catalog.repository.DentalServiceRepository;
import com.odontocare.dentists.dto.*;
import com.odontocare.dentists.model.Dentist;
import com.odontocare.dentists.repository.DentistRepository;
import com.odontocare.installation.service.ConfigurationLock;
import com.odontocare.shared.pagination.*;
import com.odontocare.shared.web.ApiException;
import com.odontocare.users.repository.UserAccountRepository;
import java.util.*;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DentistService {
  private final DentistRepository dentists;
  private final UserAccountRepository users;
  private final DentalServiceRepository services;
  private final ConfigurationLock lock;
  private final AuditService audit;

  public DentistService(
      DentistRepository dentists,
      UserAccountRepository users,
      DentalServiceRepository services,
      ConfigurationLock lock,
      AuditService audit) {
    this.dentists = dentists;
    this.users = users;
    this.services = services;
    this.lock = lock;
    this.audit = audit;
  }

  @Transactional(readOnly = true)
  public PageResponse<DentistResponse> list(PageQuery query, Boolean active) {
    return PageResponse.of(
        dentists
            .findAll(
                SearchSpecifications.<Dentist>text(
                        query.getSearch(), "fullName", "licenseNumber", "specialty")
                    .and(SearchSpecifications.equal("active", active)),
                query.pageable(
                    Map.of(
                        "name", "fullName", "license", "licenseNumber", "createdAt", "createdAt")))
            .map(this::response));
  }

  @Transactional(readOnly = true)
  public PageResponse<AssignedServiceResponse> assignedServices(
      UUID dentistId, PageQuery query, Boolean active) {
    if (!dentists.existsById(dentistId)) throw ApiException.notFound();
    var criteria =
        SearchSpecifications.<DentalService>text(query.getSearch(), "name")
            .and(SearchSpecifications.equal("active", active))
            .and(
                (root, q, cb) -> {
                  var assigned = q.subquery(UUID.class);
                  var dentist = assigned.from(Dentist.class);
                  assigned
                      .select(dentist.get("id"))
                      .where(
                          cb.equal(dentist.get("id"), dentistId),
                          cb.equal(dentist.join("services").get("id"), root.get("id")));
                  return cb.exists(assigned);
                });
    return PageResponse.of(
        services
            .findAll(criteria, query.pageable(Map.of("name", "name")))
            .map(
                service ->
                    new AssignedServiceResponse(
                        service.getId(), service.getName(), service.getActive())));
  }

  @Transactional(readOnly = true)
  public PageResponse<EligibleUserResponse> eligibleUsers(PageQuery query) {
    org.springframework.data.jpa.domain.Specification<com.odontocare.users.model.UserAccount> spec =
        SearchSpecifications.<com.odontocare.users.model.UserAccount>text(
                query.getSearch(), "displayName", "username")
            .and(SearchSpecifications.equal("active", true))
            .and(
                (root, q, cb) -> {
                  q.distinct(true);
                  return cb.equal(root.join("roles").get("code"), "DENTIST");
                });
    return PageResponse.of(
        users
            .findAll(spec, query.pageable(Map.of("name", "displayName")))
            .map(user -> new EligibleUserResponse(user.getId(), user.getDisplayName())));
  }

  @Transactional
  public DentistResponse create(DentistRequest request) {
    lock.acquire();
    if (dentists.existsByUserId(request.userId()))
      throw ApiException.conflict("Este usuario ya tiene una ficha de odontólogo.");
    if (dentists.existsByLicenseNumberIgnoreCase(request.licenseNumber().strip()))
      throw ApiException.conflict("El número de colegiatura ya está registrado.");
    var dentist = new Dentist();
    apply(dentist, request);
    dentists.saveAndFlush(dentist);
    audit.record(
        "DENTIST_CREATED",
        "DENTIST",
        dentist.getId(),
        "Creó la ficha del odontólogo " + dentist.getFullName());
    return response(dentist);
  }

  @Transactional
  public DentistResponse update(UUID id, DentistRequest request) {
    lock.acquire();
    var dentist = dentists.findById(id).orElseThrow(ApiException::notFound);
    dentist.checkVersion(request.version());
    if (dentists.existsByUserIdAndIdNot(request.userId(), id))
      throw ApiException.conflict("Este usuario ya tiene una ficha de odontólogo.");
    if (dentists.existsByLicenseNumberIgnoreCaseAndIdNot(request.licenseNumber().strip(), id))
      throw ApiException.conflict("El número de colegiatura ya está registrado.");
    apply(dentist, request);
    dentists.saveAndFlush(dentist);
    audit.record(
        "DENTIST_UPDATED",
        "DENTIST",
        id,
        "Actualizó datos, servicios y estado del odontólogo " + dentist.getFullName());
    return response(dentist);
  }

  private void apply(Dentist dentist, DentistRequest request) {
    var user =
        users
            .findWithAccessById(request.userId())
            .orElseThrow(() -> ApiException.badRequest("Selecciona un usuario existente."));
    boolean hasRole = user.getRoles().stream().anyMatch(role -> role.getCode().equals("DENTIST"));
    if (request.active() && (!user.getActive() || !hasRole))
      throw ApiException.badRequest(
          "El odontólogo activo necesita un usuario activo con rol Odontólogo.");
    Set<UUID> previous =
        dentist.getServices().stream().map(service -> service.getId()).collect(Collectors.toSet());
    var selected = services.findAllById(request.serviceIds());
    if (selected.size() != request.serviceIds().size())
      throw ApiException.badRequest("Hay servicios que no existen.");
    if (selected.stream()
        .anyMatch(service -> !service.getActive() && !previous.contains(service.getId())))
      throw ApiException.badRequest(
          "Solo puedes agregar servicios activos. Los anteriores se conservan para su historial.");
    dentist.setUser(user);
    dentist.setFullName(request.fullName().strip());
    dentist.setLicenseNumber(request.licenseNumber().strip());
    dentist.setSpecialty(request.specialty().strip());
    dentist.setActive(request.active());
    dentist.setServices(new HashSet<>(selected));
  }

  private DentistResponse response(Dentist dentist) {
    Map<UUID, String> linked =
        dentist.getServices().stream()
            .collect(Collectors.toMap(service -> service.getId(), service -> service.getName()));
    return new DentistResponse(
        dentist.getId(),
        dentist.getUser().getId(),
        dentist.getUser().getDisplayName(),
        dentist.getFullName(),
        dentist.getLicenseNumber(),
        dentist.getSpecialty(),
        dentist.getActive(),
        linked,
        dentist.getVersion());
  }
}
