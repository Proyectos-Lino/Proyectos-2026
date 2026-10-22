package com.odontocare.treatments.service;

import com.odontocare.shared.web.ApiException;
import com.odontocare.treatments.dto.TreatmentContracts.Scheduling;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Component;

/** Calendar months are always anchored to the first date, including its original day. */
@Component
public class SessionDates {
  public List<LocalDate> calculate(Scheduling config, int count) {
    if (count < 1 || count > 100)
      throw ApiException.badRequest("El número de sesiones debe estar entre 1 y 100.");
    if (config == null || "NONE".equals(config.mode())) return Collections.nCopies(count, null);
    if ("DATES".equals(config.mode())) {
      if (config.dates() == null || config.dates().size() > count)
        throw ApiException.badRequest("Las fechas no pueden superar el número de sesiones.");
      var result = new ArrayList<>(config.dates());
      while (result.size() < count) result.add(null);
      return result;
    }
    if (!"FREQUENCY".equals(config.mode())
        || config.firstDate() == null
        || config.interval() == null
        || config.interval() < 1
        || config.interval() > 365
        || config.unit() == null)
      throw ApiException.badRequest("Indica primera fecha, frecuencia positiva y unidad.");
    var result = new ArrayList<LocalDate>();
    try {
      for (int index = 0; index < count; index++) {
        long distance = (long) index * config.interval();
        result.add(
            switch (config.unit()) {
              case "DAYS" -> config.firstDate().plusDays(distance);
              case "WEEKS" -> config.firstDate().plusWeeks(distance);
              case "MONTHS" -> config.firstDate().plusMonths(distance);
              default -> throw ApiException.badRequest("Unidad de frecuencia no válida.");
            });
      }
    } catch (DateTimeException e) {
      throw ApiException.badRequest("Las fechas calculadas exceden el rango permitido.");
    }
    return result;
  }
}
