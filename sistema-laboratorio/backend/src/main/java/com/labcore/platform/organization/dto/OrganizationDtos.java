package com.labcore.platform.organization;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class OrganizationDtos {
  private OrganizationDtos() {}

  public record ContactRequest(
      @NotNull OrganizationContactType type,
      @Size(max = 80) String label,
      @NotBlank @Size(max = 180) String value) {}

  public record OrganizationRequest(
      @NotBlank @Size(max = 180) String commercialName,
      @NotBlank @Size(max = 220) String legalName,
      @Size(max = 40) String documentType,
      @Size(max = 40) String documentNumber,
      @Size(max = 400) String address,
      @Size(max = 1_500_000) String logoDataUrl,
      @NotBlank @Pattern(regexp = "^#[0-9A-Fa-f]{6}$") String primaryColor,
      @NotBlank @Pattern(regexp = "^#[0-9A-Fa-f]{6}$") String secondaryColor,
      @NotBlank @Size(max = 80) String timeZone,
      @Size(max = 5000) String legalText,
      @Size(max = 200) String accreditationName,
      @Size(max = 100) String accreditationCode,
      @Size(max = 5000) String accreditationScope,
      @NotBlank @Pattern(regexp = "^[A-Za-z0-9]{1,10}$") String serviceOrderPrefix,
      @NotBlank @Pattern(regexp = "^[A-Za-z0-9]{1,10}$") String workOrderPrefix,
      @NotBlank @Pattern(regexp = "^[A-Za-z0-9]{1,10}$") String reportPrefix,
      @NotNull List<@Valid ContactRequest> contacts,
      @NotNull List<UUID> authorizedSignerIds) {}

  public record ContactResponse(
      UUID id, OrganizationContactType type, String label, String value, int displayOrder) {}

  public record SignerResponse(UUID id, String fullName, String email, int displayOrder) {}

  public record BrandingResponse(
      String commercialName,
      String logoDataUrl,
      String primaryColor,
      String secondaryColor,
      String timeZone) {}

  public record OrganizationResponse(
      UUID id,
      String commercialName,
      String legalName,
      String documentType,
      String documentNumber,
      String address,
      String logoDataUrl,
      String primaryColor,
      String secondaryColor,
      String timeZone,
      String legalText,
      String accreditationName,
      String accreditationCode,
      String accreditationScope,
      String serviceOrderPrefix,
      String workOrderPrefix,
      String reportPrefix,
      List<ContactResponse> contacts,
      List<SignerResponse> authorizedSigners,
      Instant updatedAt) {}

  public record DocumentIdentity(
      String commercialName,
      String legalName,
      String documentLabel,
      String address,
      String logoDataUrl,
      String primaryColor,
      String secondaryColor,
      String timeZone,
      String legalText,
      String accreditationName,
      String accreditationCode,
      String accreditationScope,
      List<ContactResponse> contacts,
      List<SignerResponse> authorizedSigners) {}
}
