package com.odontocare.schedules.service;

import com.odontocare.schedules.model.*;
import com.odontocare.shared.web.ApiException;
import java.util.List;

public final class PeriodValidator {
  private PeriodValidator() {}

  public static void validate(List<WeeklyPeriod> periods) {
    if (periods.size() > 48)
      throw ApiException.badRequest(
          "La jornada tiene demasiados intervalos. Simplifica su configuración.");
    for (var period : periods) {
      if (period.getEndMinute() <= period.getStartMinute())
        throw ApiException.badRequest("La hora de fin debe ser posterior a la hora de inicio.");
      if (period.getKind() == PeriodKind.BREAK
          && periods.stream()
              .noneMatch(
                  work ->
                      work.getKind() == PeriodKind.WORK
                          && work.getStartMinute() <= period.getStartMinute()
                          && work.getEndMinute() >= period.getEndMinute())) {
        throw ApiException.conflict(
            "Cada descanso debe estar dentro de una jornada activa del mismo día.");
      }
    }
    for (int first = 0; first < periods.size(); first++) {
      for (int second = first + 1; second < periods.size(); second++) {
        var a = periods.get(first);
        var b = periods.get(second);
        if (a.getKind() == b.getKind()
            && a.getStartMinute() < b.getEndMinute()
            && b.getStartMinute() < a.getEndMinute()) {
          throw ApiException.conflict(
              "Los intervalos del mismo tipo no pueden superponerse para un odontólogo y día.");
        }
      }
    }
  }
}
