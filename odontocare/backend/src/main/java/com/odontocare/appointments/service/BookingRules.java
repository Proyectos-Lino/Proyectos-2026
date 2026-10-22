package com.odontocare.appointments.service;

import com.odontocare.catalog.model.DentalService;
import com.odontocare.dentists.model.Dentist;
import com.odontocare.shared.web.ApiException;
import java.time.*;
import org.springframework.stereotype.Component;

@Component
public class BookingRules {
  public void eligible(Dentist dentist, DentalService service) {
    if (!dentist.getActive() || !dentist.getUser().getActive())
      throw ApiException.badRequest("El odontólogo no está activo.");
    if (service != null
        && (!service.getActive()
            || !service.getCategory().getActive()
            || dentist.getServices().stream().noneMatch(x -> x.getId().equals(service.getId()))))
      throw ApiException.badRequest(
          "El servicio no está activo o no está asignado a este odontólogo.");
  }

  public Instant instant(LocalDateTime local, ZoneId zone) {
    if (local.getSecond() != 0 || local.getNano() != 0)
      throw ApiException.badRequest("Indica el horario con precisión de minutos.");
    var offsets = zone.getRules().getValidOffsets(local);
    if (offsets.size() != 1)
      throw ApiException.badRequest(
          "El horario es inexistente o ambiguo por el cambio de hora de la zona del consultorio.");
    return local.toInstant(offsets.getFirst());
  }
}
