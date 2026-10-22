package com.odontocare.clinical.service;

import com.odontocare.dentists.model.Dentist;
import com.odontocare.dentists.repository.DentistRepository;
import com.odontocare.installation.repository.InstallationProfileRepository;
import com.odontocare.security.model.AccountPrincipal;
import com.odontocare.shared.web.ApiException;
import java.time.*;
import java.util.UUID;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component
public class ClinicalAccess {
  private final DentistRepository dentists;
  private final InstallationProfileRepository profiles;
  private final Clock clock;

  public ClinicalAccess(
      DentistRepository dentists, InstallationProfileRepository profiles, Clock clock) {
    this.dentists = dentists;
    this.profiles = profiles;
    this.clock = clock;
  }

  public AccountPrincipal actor() {
    var auth = SecurityContextHolder.getContext().getAuthentication();
    if (auth == null || !(auth.getPrincipal() instanceof AccountPrincipal))
      throw ApiException.forbidden();
    return (AccountPrincipal) auth.getPrincipal();
  }

  public Dentist requireDentist(UUID id) {
    var dentist = dentists.findById(id).orElseThrow(ApiException::notFound);
    if (!dentist.getActive() || !dentist.getUser().getActive())
      throw ApiException.badRequest("Selecciona un odontólogo activo con cuenta activa.");
    if (!actor().getRoleCodes().contains("ADMIN")
        && !dentist.getUser().getId().equals(actor().getId())) throw ApiException.forbidden();
    return dentist;
  }

  public String dentistName(UUID id) {
    return dentists.findById(id).orElseThrow(ApiException::notFound).getFullName();
  }

  public LocalDate today() {
    return LocalDate.now(
        clock.withZone(ZoneId.of(profiles.findById((short) 1).orElseThrow().getTimeZone())));
  }

  public void requireDate(LocalDate date) {
    if (date.isAfter(today()))
      throw ApiException.badRequest("La fecha clínica no puede estar en el futuro.");
  }

  public static void requireTooth(Integer tooth) {
    if (tooth == null) return;
    int quadrant = tooth / 10, position = tooth % 10;
    if (!((quadrant >= 1 && quadrant <= 4 && position >= 1 && position <= 8)
        || (quadrant >= 5 && quadrant <= 8 && position >= 1 && position <= 5)))
      throw ApiException.badRequest("La pieza dental no es válida en numeración FDI.");
  }
}
