package com.labcore.platform.organization;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

import com.labcore.platform.common.exception.BusinessRuleViolationException;
import com.labcore.platform.security.ApplicationUser;
import com.labcore.platform.security.ApplicationUserRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OrganizationProfileServiceTest {
  @Mock private OrganizationProfileRepository profileRepository;
  @Mock private OrganizationContactRepository contactRepository;
  @Mock private OrganizationAuthorizedSignerRepository signerRepository;
  @Mock private ApplicationUserRepository userRepository;
  @Mock private OrganizationProfileHistoryRepository historyRepository;
  @Mock private OrganizationProfile profile;
  @Mock private ApplicationUser user;

  @Test
  void rejectsAnUnknownTimeZone() {
    assertThatThrownBy(() -> service().update(request("Zona/Desconocida"), "admin@test"))
        .isInstanceOf(BusinessRuleViolationException.class)
        .hasMessage("La zona horaria indicada no es válida.");
  }

  @Test
  void restrictsIssuanceWhenAuthorizedSignersAreConfigured() {
    UUID organizationId = UUID.randomUUID();
    given(profileRepository.findByInstallationKey(OrganizationProfile.PRIMARY_INSTALLATION))
        .willReturn(Optional.of(profile));
    given(profile.getId()).willReturn(organizationId);
    given(signerRepository.findByOrganizationIdOrderByDisplayOrderAsc(organizationId))
        .willReturn(
            List.of(new OrganizationAuthorizedSigner(organizationId, UUID.randomUUID(), 0)));
    given(user.getId()).willReturn(UUID.randomUUID());

    assertThatThrownBy(() -> service().ensureAuthorizedSigner(user))
        .isInstanceOf(BusinessRuleViolationException.class)
        .hasMessage(
            "El usuario no figura entre los responsables autorizados para emitir informes.");
  }

  @Test
  void rejectsRepeatedDocumentPrefixes() {
    OrganizationDtos.OrganizationRequest valid = request("America/Lima");
    OrganizationDtos.OrganizationRequest repeated =
        new OrganizationDtos.OrganizationRequest(
            valid.commercialName(),
            valid.legalName(),
            valid.documentType(),
            valid.documentNumber(),
            valid.address(),
            valid.logoDataUrl(),
            valid.primaryColor(),
            valid.secondaryColor(),
            valid.timeZone(),
            valid.legalText(),
            valid.accreditationName(),
            valid.accreditationCode(),
            valid.accreditationScope(),
            "DOC",
            "DOC",
            "IR",
            valid.contacts(),
            valid.authorizedSignerIds());

    assertThatThrownBy(() -> service().update(repeated, "admin@test"))
        .isInstanceOf(BusinessRuleViolationException.class)
        .hasMessage("Los prefijos de OS, OT e informes deben ser diferentes entre sí.");
  }

  private OrganizationDtos.OrganizationRequest request(String timeZone) {
    return new OrganizationDtos.OrganizationRequest(
        "Laboratorio",
        "Laboratorio S.A.C.",
        "RUC",
        "20123456789",
        "Lima",
        null,
        "#0F766E",
        "#0F172A",
        timeZone,
        null,
        null,
        null,
        null,
        "OS",
        "OT",
        "IR",
        List.of(),
        List.of());
  }

  private OrganizationProfileService service() {
    return new OrganizationProfileService(
        profileRepository, contactRepository, signerRepository, userRepository, historyRepository);
  }
}
