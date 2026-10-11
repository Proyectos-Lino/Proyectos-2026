package com.labcore.platform.organization;

import com.labcore.platform.common.BaseEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "organization_profile")
public class OrganizationProfile extends BaseEntity {
  public static final String PRIMARY_INSTALLATION = "PRIMARY";

  @Id private UUID id;
  private String installationKey;
  private String commercialName;
  private String legalName;
  private String documentType;
  private String documentNumber;
  private String address;
  private String logoDataUrl;
  private String primaryColor;
  private String secondaryColor;
  private String timeZone;
  private String legalText;
  private String accreditationName;
  private String accreditationCode;
  private String accreditationScope;
  private String serviceOrderPrefix;
  private String workOrderPrefix;
  private String reportPrefix;

  protected OrganizationProfile() {}

  public UUID getId() {
    return id;
  }

  public String getCommercialName() {
    return commercialName;
  }

  public String getLegalName() {
    return legalName;
  }

  public String getDocumentType() {
    return documentType;
  }

  public String getDocumentNumber() {
    return documentNumber;
  }

  public String getAddress() {
    return address;
  }

  public String getLogoDataUrl() {
    return logoDataUrl;
  }

  public String getPrimaryColor() {
    return primaryColor;
  }

  public String getSecondaryColor() {
    return secondaryColor;
  }

  public String getTimeZone() {
    return timeZone;
  }

  public String getLegalText() {
    return legalText;
  }

  public String getAccreditationName() {
    return accreditationName;
  }

  public String getAccreditationCode() {
    return accreditationCode;
  }

  public String getAccreditationScope() {
    return accreditationScope;
  }

  public String getServiceOrderPrefix() {
    return serviceOrderPrefix;
  }

  public String getWorkOrderPrefix() {
    return workOrderPrefix;
  }

  public String getReportPrefix() {
    return reportPrefix;
  }

  public void update(OrganizationDtos.OrganizationRequest request) {
    commercialName = request.commercialName().trim();
    legalName = request.legalName().trim();
    documentType = trimToNull(request.documentType());
    documentNumber = trimToNull(request.documentNumber());
    address = trimToNull(request.address());
    logoDataUrl = trimToNull(request.logoDataUrl());
    primaryColor = request.primaryColor().toUpperCase(java.util.Locale.ROOT);
    secondaryColor = request.secondaryColor().toUpperCase(java.util.Locale.ROOT);
    timeZone = request.timeZone().trim();
    legalText = trimToNull(request.legalText());
    accreditationName = trimToNull(request.accreditationName());
    accreditationCode = trimToNull(request.accreditationCode());
    accreditationScope = trimToNull(request.accreditationScope());
    serviceOrderPrefix = normalizePrefix(request.serviceOrderPrefix());
    workOrderPrefix = normalizePrefix(request.workOrderPrefix());
    reportPrefix = normalizePrefix(request.reportPrefix());
  }

  private String normalizePrefix(String value) {
    return value.trim().toUpperCase(java.util.Locale.ROOT);
  }

  private String trimToNull(String value) {
    return value == null || value.isBlank() ? null : value.trim();
  }
}
