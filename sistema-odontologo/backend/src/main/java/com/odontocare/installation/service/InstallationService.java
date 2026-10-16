package com.odontocare.installation.service;

import com.odontocare.audit.service.AuditService;
import com.odontocare.installation.dto.*;
import com.odontocare.installation.model.InstallationProfile;
import com.odontocare.installation.repository.*;
import com.odontocare.shared.web.ApiException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InstallationService {
  private final InstallationProfileRepository repository;
  private final InstallationLogoRepository logos;
  private final ConfigurationLock lock;
  private final AuditService audit;

  public InstallationService(
      InstallationProfileRepository repository,
      InstallationLogoRepository logos,
      ConfigurationLock lock,
      AuditService audit) {
    this.repository = repository;
    this.logos = logos;
    this.lock = lock;
    this.audit = audit;
  }

  @Transactional(readOnly = true)
  public InstallationResponse getInstallation() {
    var profile = profile();
    return new InstallationResponse(
        profile.getDisplayName(),
        profile.getTimeZone(),
        profile.getCurrency(),
        profile.getBrandColor(),
        profile.getAccentColor(),
        profile.getDateFormat(),
        logos.existsById((short) 1),
        profile.getLogoRevision());
  }

  @Transactional(readOnly = true)
  public SettingsResponse getSettings() {
    return response(profile());
  }

  @Transactional
  public SettingsResponse update(SettingsRequest request) {
    SettingsValidator.validate(request);
    var profile = lock.acquire();
    if (request.version() != profile.getVersion())
      throw ApiException.conflict("La configuración cambió. Actualiza los datos antes de guardar.");
    if (request.patientNextNumber() < profile.getPatientNextNumber()
        || request.receiptNextNumber() < profile.getReceiptNextNumber()
        || request.budgetNextNumber() < profile.getBudgetNextNumber()) {
      throw ApiException.badRequest(
          "Los correlativos no pueden retroceder: conserva o aumenta el siguiente número.");
    }
    profile.setDisplayName(request.displayName().strip());
    profile.setTimeZone(request.timeZone().strip());
    profile.setCurrency(request.currency().strip());
    profile.setLegalName(request.legalName().strip());
    profile.setAddress(request.address().strip());
    profile.setPhone(request.phone().strip());
    profile.setEmail(request.email().strip());
    profile.setBrandColor(request.brandColor().strip());
    profile.setAccentColor(request.accentColor().strip());
    profile.setDateFormat(request.dateFormat().strip());
    profile.setDocumentHeader(request.documentHeader().strip());
    profile.setDocumentFooter(request.documentFooter().strip());
    profile.setAppointmentInstructions(request.appointmentInstructions().strip());
    profile.setPatientPrefix(request.patientPrefix().strip());
    profile.setPatientNextNumber(request.patientNextNumber());
    profile.setReceiptPrefix(request.receiptPrefix().strip());
    profile.setReceiptNextNumber(request.receiptNextNumber());
    profile.setBudgetPrefix(request.budgetPrefix().strip());
    profile.setBudgetNextNumber(request.budgetNextNumber());
    profile.setMinimumLeadMinutes(request.minimumLeadMinutes());
    profile.setAppointmentGapMinutes(request.appointmentGapMinutes());
    repository.saveAndFlush(profile);
    audit.record(
        "SETTINGS_UPDATED",
        "SETTINGS",
        1,
        "Actualizó identidad, reglas de reserva, formatos y documentos del consultorio.");
    return response(profile);
  }

  private InstallationProfile profile() {
    return repository
        .findById((short) 1)
        .orElseThrow(() -> new IllegalStateException("Installation missing"));
  }

  private SettingsResponse response(InstallationProfile profile) {
    return new SettingsResponse(
        profile.getDisplayName(),
        profile.getTimeZone(),
        profile.getCurrency(),
        profile.getLegalName(),
        profile.getAddress(),
        profile.getPhone(),
        profile.getEmail(),
        profile.getBrandColor(),
        profile.getAccentColor(),
        profile.getDateFormat(),
        profile.getDocumentHeader(),
        profile.getDocumentFooter(),
        profile.getAppointmentInstructions(),
        profile.getPatientPrefix(),
        profile.getPatientNextNumber(),
        profile.getReceiptPrefix(),
        profile.getReceiptNextNumber(),
        profile.getBudgetPrefix(),
        profile.getBudgetNextNumber(),
        profile.getMinimumLeadMinutes(),
        profile.getAppointmentGapMinutes(),
        profile.getVersion(),
        logos.existsById((short) 1),
        profile.getLogoRevision());
  }
}
