package com.odontocare.appointments.service;

import com.odontocare.appointments.model.*;
import com.odontocare.shared.web.ApiException;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Component;

@Component
public class AppointmentStateRules {
  private static final Map<AppointmentStatus, Set<AppointmentStatus>> NEXT =
      Map.of(
          AppointmentStatus.RESERVED,
          Set.of(
              AppointmentStatus.CONFIRMED,
              AppointmentStatus.WAITING,
              AppointmentStatus.CANCELLED,
              AppointmentStatus.NO_SHOW),
          AppointmentStatus.CONFIRMED,
          Set.of(AppointmentStatus.WAITING, AppointmentStatus.CANCELLED, AppointmentStatus.NO_SHOW),
          AppointmentStatus.WAITING,
          Set.of(
              AppointmentStatus.IN_PROGRESS,
              AppointmentStatus.CANCELLED,
              AppointmentStatus.NO_SHOW),
          AppointmentStatus.IN_PROGRESS,
          Set.of(AppointmentStatus.ATTENDED),
          AppointmentStatus.ATTENDED,
          Set.of(),
          AppointmentStatus.CANCELLED,
          Set.of(),
          AppointmentStatus.NO_SHOW,
          Set.of());

  public void requireTransition(
      Appointment appointment, AppointmentStatus next, String reason, Clock clock) {
    if (!NEXT.get(appointment.getStatus()).contains(next))
      throw ApiException.conflict("El cambio de estado no está permitido desde el estado actual.");
    if ((next == AppointmentStatus.CANCELLED || next == AppointmentStatus.NO_SHOW)
        && reason.isBlank()) throw ApiException.badRequest("Indica el motivo del cambio.");
    if (Set.of(
                AppointmentStatus.WAITING,
                AppointmentStatus.IN_PROGRESS,
                AppointmentStatus.ATTENDED,
                AppointmentStatus.NO_SHOW)
            .contains(next)
        && appointment.getStartsAt().isAfter(clock.instant()))
      throw ApiException.badRequest(
          "Este estado solo está disponible cuando ha llegado la hora de la cita.");
  }

  public void requireReschedulable(Appointment appointment) {
    if (!Set.of(AppointmentStatus.RESERVED, AppointmentStatus.CONFIRMED)
        .contains(appointment.getStatus()))
      throw ApiException.conflict("Solo puedes reprogramar una cita reservada o confirmada.");
  }
}
