package com.odontocare.appointments.service;

import com.odontocare.appointments.dto.*;
import com.odontocare.appointments.model.Appointment;
import com.odontocare.appointments.repository.AppointmentRepository;
import com.odontocare.catalog.repository.DentalServiceRepository;
import com.odontocare.dentists.repository.DentistRepository;
import com.odontocare.installation.model.InstallationProfile;
import com.odontocare.installation.repository.InstallationProfileRepository;
import com.odontocare.schedules.model.*;
import com.odontocare.schedules.repository.*;
import com.odontocare.shared.pagination.*;
import com.odontocare.shared.web.ApiException;
import java.time.*;
import java.util.*;
import org.springframework.data.domain.PageImpl;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AvailabilityService {
  private final WeeklyPeriodRepository periods;
  private final ScheduleExceptionRepository exceptions;
  private final AppointmentRepository appointments;
  private final DentistRepository dentists;
  private final DentalServiceRepository services;
  private final InstallationProfileRepository profiles;
  private final BookingRules rules;
  private final Clock clock;

  public AvailabilityService(
      WeeklyPeriodRepository periods,
      ScheduleExceptionRepository exceptions,
      AppointmentRepository appointments,
      DentistRepository dentists,
      DentalServiceRepository services,
      InstallationProfileRepository profiles,
      BookingRules rules,
      Clock clock) {
    this.periods = periods;
    this.exceptions = exceptions;
    this.appointments = appointments;
    this.dentists = dentists;
    this.services = services;
    this.profiles = profiles;
    this.rules = rules;
    this.clock = clock;
  }

  public record Slot(
      Instant startsAt, Instant endsAt, LocalDateTime localStart, LocalDateTime localEnd) {}

  private record DayData(
      List<WeeklyPeriod> periods,
      List<ScheduleException> exceptions,
      List<Appointment> appointments,
      ZoneId zone) {}

  private DayData loadDay(UUID dentist, LocalDate date, ZoneId zone) {
    var blocks =
        exceptions.findAll(
            (root, criteriaQuery, cb) ->
                cb.and(
                    cb.isTrue(root.get("active")),
                    cb.lessThanOrEqualTo(root.get("startDate"), date),
                    cb.greaterThanOrEqualTo(root.get("endDate"), date),
                    cb.or(
                        cb.isNull(root.get("dentist")),
                        cb.equal(root.get("dentist").get("id"), dentist))));
    return new DayData(
        periods.findByDentistIdAndDayOfWeekAndActiveTrue(dentist, date.getDayOfWeek().getValue()),
        blocks,
        appointments.occupied(
            dentist,
            date.atStartOfDay(zone).toInstant().minusSeconds(7200),
            date.plusDays(1).atStartOfDay(zone).toInstant().plusSeconds(7200)),
        zone);
  }

  @Transactional(propagation = org.springframework.transaction.annotation.Propagation.MANDATORY)
  public void requireAvailable(
      UUID dentist, Instant start, int duration, InstallationProfile profile, UUID excluded) {
    ZoneId zone = ZoneId.of(profile.getTimeZone());
    LocalDate date = start.atZone(zone).toLocalDate();
    String failure = unavailable(start, duration, profile, excluded, loadDay(dentist, date, zone));
    if (failure != null) throw ApiException.conflict(failure);
  }

  private String unavailable(
      Instant start, int duration, InstallationProfile profile, UUID excluded, DayData dayData) {
    if (start.isBefore(clock.instant().plusSeconds(profile.getMinimumLeadMinutes() * 60L)))
      return "La cita no respeta la anticipación mínima del consultorio.";
    Instant end = start.plusSeconds(duration * 60L);
    var localStart = start.atZone(dayData.zone());
    var localEnd = end.atZone(dayData.zone());
    int from = localStart.getHour() * 60 + localStart.getMinute();
    int to = localEnd.getHour() * 60 + localEnd.getMinute();
    if (localEnd.toLocalDate().equals(localStart.toLocalDate().plusDays(1)) && to == 0) to = 1440;
    else if (!localStart.toLocalDate().equals(localEnd.toLocalDate()))
      return "La cita debe terminar dentro de la jornada del día.";
    final int until = to;
    if (dayData.periods().stream()
        .noneMatch(
            w ->
                w.getKind() == PeriodKind.WORK
                    && w.getStartMinute() <= from
                    && w.getEndMinute() >= until))
      return "El intervalo completo está fuera de la jornada.";
    if (dayData.periods().stream()
        .anyMatch(
            w ->
                w.getKind() == PeriodKind.BREAK
                    && w.getStartMinute() < until
                    && w.getEndMinute() > from)) return "El intervalo coincide con un descanso.";
    if (dayData.exceptions().stream()
        .anyMatch(
            e ->
                e.getStartMinute() == null
                    || (e.getStartMinute() < until && e.getEndMinute() > from)))
      return "El intervalo coincide con un día no laborable o una ausencia.";
    for (var appointment : dayData.appointments()) {
      if (appointment.getId().equals(excluded)) continue;
      int gap = Math.max(profile.getAppointmentGapMinutes(), appointment.getGapMinutes());
      if (start.isBefore(appointment.getEndsAt().plusSeconds(gap * 60L))
          && end.plusSeconds(gap * 60L).isAfter(appointment.getStartsAt()))
        return "El intervalo está ocupado o no respeta la separación entre citas.";
    }
    return null;
  }

  @Transactional(readOnly = true)
  public PageResponse<Slot> slots(
      UUID dentistId,
      UUID serviceId,
      Integer manualDuration,
      LocalDate date,
      UUID appointmentId,
      PageQuery query) {
    var profile = profiles.findById((short) 1).orElseThrow();
    var dentist = dentists.findById(dentistId).orElseThrow(ApiException::notFound);
    var service =
        serviceId == null ? null : services.findById(serviceId).orElseThrow(ApiException::notFound);
    rules.eligible(dentist, service);
    int duration =
        service == null
            ? (manualDuration == null ? 0 : manualDuration)
            : service.getDurationMinutes();
    if (duration < 1 || duration > 1440)
      throw ApiException.badRequest("Indica una duración entre 1 y 1440 minutos.");
    if (appointmentId != null && !appointments.existsById(appointmentId))
      throw ApiException.notFound();
    var zone = ZoneId.of(profile.getTimeZone());
    var data = loadDay(dentistId, date, zone);
    List<Slot> slots = new ArrayList<>();
    for (int minute = 0; minute < 1440; minute += 15) {
      var local = date.atStartOfDay().plusMinutes(minute);
      if (zone.getRules().getValidOffsets(local).size() != 1) continue;
      Instant start = rules.instant(local, zone);
      if (unavailable(start, duration, profile, appointmentId, data) == null
          && (query.getSearch().isEmpty()
              || local.toLocalTime().toString().contains(query.getSearch())))
        slots.add(
            new Slot(
                start,
                start.plusSeconds(duration * 60L),
                local,
                LocalDateTime.ofInstant(start.plusSeconds(duration * 60L), zone)));
    }
    var pageable = query.pageable(Map.of("name", "startsAt", "startsAt", "startsAt"));
    if (query.getDirection().equals("desc")) Collections.reverse(slots);
    int from = (int) Math.min(pageable.getOffset(), slots.size());
    int to = Math.min(from + pageable.getPageSize(), slots.size());
    return PageResponse.of(new PageImpl<>(slots.subList(from, to), pageable, slots.size()));
  }
}
