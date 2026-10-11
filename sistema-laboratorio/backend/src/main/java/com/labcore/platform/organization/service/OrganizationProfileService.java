package com.labcore.platform.organization;

import com.labcore.platform.common.exception.BusinessRuleViolationException;
import com.labcore.platform.security.ApplicationUser;
import com.labcore.platform.security.ApplicationUserRepository;
import com.labcore.platform.security.RoleCode;
import java.time.DateTimeException;
import java.time.ZoneId;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class OrganizationProfileService {
  private static final Set<RoleCode> SIGNER_ROLES =
      Set.of(RoleCode.ADMIN, RoleCode.TECHNICAL_MANAGER, RoleCode.VALIDATOR);
  private static final java.util.regex.Pattern EMAIL_PATTERN =
      java.util.regex.Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");

  private final OrganizationProfileRepository profileRepository;
  private final OrganizationContactRepository contactRepository;
  private final OrganizationAuthorizedSignerRepository signerRepository;
  private final ApplicationUserRepository userRepository;
  private final OrganizationProfileHistoryRepository historyRepository;

  public OrganizationProfileService(
      OrganizationProfileRepository profileRepository,
      OrganizationContactRepository contactRepository,
      OrganizationAuthorizedSignerRepository signerRepository,
      ApplicationUserRepository userRepository,
      OrganizationProfileHistoryRepository historyRepository) {
    this.profileRepository = profileRepository;
    this.contactRepository = contactRepository;
    this.signerRepository = signerRepository;
    this.userRepository = userRepository;
    this.historyRepository = historyRepository;
  }

  @Transactional(readOnly = true)
  public OrganizationDtos.OrganizationResponse get() {
    return toResponse(requiredProfile());
  }

  @Transactional(readOnly = true)
  public OrganizationDtos.BrandingResponse branding() {
    OrganizationProfile profile = requiredProfile();
    return new OrganizationDtos.BrandingResponse(
        profile.getCommercialName(),
        profile.getLogoDataUrl(),
        profile.getPrimaryColor(),
        profile.getSecondaryColor(),
        profile.getTimeZone());
  }

  @Transactional(readOnly = true)
  public OrganizationDtos.DocumentIdentity documentIdentity() {
    OrganizationDtos.OrganizationResponse profile = get();
    String documentLabel = joinNonBlank(" ", profile.documentType(), profile.documentNumber());
    return new OrganizationDtos.DocumentIdentity(
        profile.commercialName(),
        profile.legalName(),
        documentLabel,
        profile.address(),
        profile.logoDataUrl(),
        profile.primaryColor(),
        profile.secondaryColor(),
        profile.timeZone(),
        profile.legalText(),
        profile.accreditationName(),
        profile.accreditationCode(),
        profile.accreditationScope(),
        profile.contacts(),
        profile.authorizedSigners());
  }

  public OrganizationDtos.OrganizationResponse update(
      OrganizationDtos.OrganizationRequest request, String performedBy) {
    validate(request);
    OrganizationProfile profile = requiredProfile();
    List<ApplicationUser> signers = validateSigners(request.authorizedSignerIds());
    profile.update(request);
    contactRepository.deleteByOrganizationId(profile.getId());
    signerRepository.deleteByOrganizationId(profile.getId());
    for (int index = 0; index < request.contacts().size(); index++) {
      OrganizationDtos.ContactRequest contact = request.contacts().get(index);
      contactRepository.save(
          new OrganizationContact(
              profile.getId(),
              contact.type(),
              trimToNull(contact.label()),
              contact.value().trim(),
              index));
    }
    for (int index = 0; index < signers.size(); index++) {
      signerRepository.save(
          new OrganizationAuthorizedSigner(profile.getId(), signers.get(index).getId(), index));
    }
    historyRepository.save(new OrganizationProfileHistory(profile.getId(), performedBy));
    return toResponse(profile);
  }

  @Transactional(readOnly = true)
  public String prefixFor(String documentType) {
    OrganizationProfile profile = requiredProfile();
    return switch (documentType.toUpperCase(Locale.ROOT)) {
      case "OS" -> profile.getServiceOrderPrefix();
      case "OT" -> profile.getWorkOrderPrefix();
      case "IR" -> profile.getReportPrefix();
      default -> throw new BusinessRuleViolationException("El tipo documental no es válido.");
    };
  }

  @Transactional(readOnly = true)
  public ZoneId zoneId() {
    return ZoneId.of(requiredProfile().getTimeZone());
  }

  @Transactional(readOnly = true)
  public void ensureAuthorizedSigner(ApplicationUser user) {
    List<OrganizationAuthorizedSigner> signers =
        signerRepository.findByOrganizationIdOrderByDisplayOrderAsc(requiredProfile().getId());
    if (!signers.isEmpty()
        && signers.stream().noneMatch(signer -> signer.getUserId().equals(user.getId()))) {
      throw new BusinessRuleViolationException(
          "El usuario no figura entre los responsables autorizados para emitir informes.");
    }
  }

  private void validate(OrganizationDtos.OrganizationRequest request) {
    try {
      ZoneId.of(request.timeZone().trim());
    } catch (DateTimeException exception) {
      throw new BusinessRuleViolationException("La zona horaria indicada no es válida.");
    }
    if ((trimToNull(request.documentType()) == null)
        != (trimToNull(request.documentNumber()) == null)) {
      throw new BusinessRuleViolationException(
          "El tipo y el número de documento deben registrarse juntos.");
    }
    String logo = trimToNull(request.logoDataUrl());
    if (logo != null
        && !(logo.startsWith("data:image/png;base64,")
            || logo.startsWith("data:image/jpeg;base64,")
            || logo.startsWith("data:image/webp;base64,"))) {
      throw new BusinessRuleViolationException("El logo debe ser una imagen PNG, JPG o WebP.");
    }
    Set<String> contactKeys = new HashSet<>();
    for (OrganizationDtos.ContactRequest contact : request.contacts()) {
      String value = contact.value().trim();
      if (contact.type() == OrganizationContactType.EMAIL
          && !EMAIL_PATTERN.matcher(value).matches()) {
        throw new BusinessRuleViolationException(
            "Uno de los correos institucionales no es válido.");
      }
      if (!contactKeys.add(contact.type() + ":" + value.toLowerCase(Locale.ROOT))) {
        throw new BusinessRuleViolationException("No se pueden repetir contactos institucionales.");
      }
    }
    Set<String> prefixes =
        new HashSet<>(
            List.of(
                request.serviceOrderPrefix().trim().toUpperCase(Locale.ROOT),
                request.workOrderPrefix().trim().toUpperCase(Locale.ROOT),
                request.reportPrefix().trim().toUpperCase(Locale.ROOT)));
    if (prefixes.size() != 3) {
      throw new BusinessRuleViolationException(
          "Los prefijos de OS, OT e informes deben ser diferentes entre sí.");
    }
  }

  private List<ApplicationUser> validateSigners(List<UUID> signerIds) {
    if (new HashSet<>(signerIds).size() != signerIds.size()) {
      throw new BusinessRuleViolationException("No se puede repetir un responsable autorizado.");
    }
    var usersById =
        userRepository.findAllById(signerIds).stream()
            .collect(Collectors.toMap(ApplicationUser::getId, Function.identity()));
    return signerIds.stream()
        .map(
            signerId -> {
              ApplicationUser user = usersById.get(signerId);
              if (user == null || !user.isActive()) {
                throw new BusinessRuleViolationException(
                    "Todos los responsables autorizados deben ser usuarios activos.");
              }
              if (user.getRoles().stream()
                  .noneMatch(role -> SIGNER_ROLES.contains(role.getCode()))) {
                throw new BusinessRuleViolationException(
                    "Un responsable autorizado requiere un rol de revisión o dirección técnica.");
              }
              return user;
            })
        .toList();
  }

  private OrganizationDtos.OrganizationResponse toResponse(OrganizationProfile profile) {
    List<OrganizationDtos.ContactResponse> contacts =
        contactRepository.findByOrganizationIdOrderByDisplayOrderAsc(profile.getId()).stream()
            .map(
                contact ->
                    new OrganizationDtos.ContactResponse(
                        contact.getId(),
                        contact.getContactType(),
                        contact.getLabel(),
                        contact.getValue(),
                        contact.getDisplayOrder()))
            .toList();
    List<OrganizationAuthorizedSigner> assignments =
        signerRepository.findByOrganizationIdOrderByDisplayOrderAsc(profile.getId());
    var usersById =
        userRepository
            .findAllById(assignments.stream().map(OrganizationAuthorizedSigner::getUserId).toList())
            .stream()
            .collect(Collectors.toMap(ApplicationUser::getId, Function.identity()));
    List<OrganizationDtos.SignerResponse> signers =
        assignments.stream()
            .filter(assignment -> usersById.containsKey(assignment.getUserId()))
            .map(
                assignment -> {
                  ApplicationUser user = usersById.get(assignment.getUserId());
                  return new OrganizationDtos.SignerResponse(
                      user.getId(),
                      user.getFullName(),
                      user.getEmail(),
                      assignment.getDisplayOrder());
                })
            .toList();
    return new OrganizationDtos.OrganizationResponse(
        profile.getId(),
        profile.getCommercialName(),
        profile.getLegalName(),
        profile.getDocumentType(),
        profile.getDocumentNumber(),
        profile.getAddress(),
        profile.getLogoDataUrl(),
        profile.getPrimaryColor(),
        profile.getSecondaryColor(),
        profile.getTimeZone(),
        profile.getLegalText(),
        profile.getAccreditationName(),
        profile.getAccreditationCode(),
        profile.getAccreditationScope(),
        profile.getServiceOrderPrefix(),
        profile.getWorkOrderPrefix(),
        profile.getReportPrefix(),
        contacts,
        signers,
        profile.getUpdatedAt());
  }

  private OrganizationProfile requiredProfile() {
    return profileRepository
        .findByInstallationKey(OrganizationProfile.PRIMARY_INSTALLATION)
        .orElseThrow(
            () ->
                new IllegalStateException(
                    "La configuración principal de la organización no está disponible."));
  }

  private String joinNonBlank(String separator, String... values) {
    return java.util.Arrays.stream(values)
        .map(this::trimToNull)
        .filter(java.util.Objects::nonNull)
        .collect(Collectors.joining(separator));
  }

  private String trimToNull(String value) {
    return value == null || value.isBlank() ? null : value.trim();
  }
}
