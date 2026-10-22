package com.odontocare.patients.service;

import com.odontocare.patients.dto.PatientRequest;
import com.odontocare.shared.web.ApiException;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Component;

@Component
public class PatientValidator {
  private final Clock clock;

  public PatientValidator(Clock clock) {
    this.clock = clock;
  }

  public void validate(PatientRequest request, ZoneId zone) {
    if (!request.provisional() && request.birthDate() == null)
      throw ApiException.badRequest(
          "Completa la fecha de nacimiento o marca la ficha como provisional.");
    if (request.birthDate() != null
        && request.birthDate().isAfter(LocalDate.now(clock.withZone(zone))))
      throw ApiException.badRequest("La fecha de nacimiento no puede ser futura.");
    if (request.birthDate() != null
        && request.birthDate().isAfter(LocalDate.now(clock.withZone(zone)).minusYears(18))
        && request.contacts().stream().noneMatch(PatientRequest.ContactRequest::guardian))
      throw ApiException.badRequest("Un menor necesita un contacto identificado como responsable.");
    if (request.documentType().isBlank() != request.documentNumber().isBlank())
      throw ApiException.badRequest("Indica el tipo y el número del documento juntos.");
    if (request.contacts().stream().map(PatientRequest.ContactRequest::phone).distinct().count()
        != request.contacts().size())
      throw ApiException.badRequest("No repitas un teléfono dentro de la misma ficha.");
  }

  public String duplicateKey(PatientRequest request) {

    return request.fullName().strip().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT)
        + "|"
        + request.birthDate()
        + "|"
        + request.contacts().stream()
            .map(PatientRequest.ContactRequest::phone)
            .sorted()
            .findFirst()
            .orElse("");
  }
}
