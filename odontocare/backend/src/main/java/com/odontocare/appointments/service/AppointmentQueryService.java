package com.odontocare.appointments.service;

import com.odontocare.appointments.dto.*;
import com.odontocare.appointments.model.*;
import com.odontocare.appointments.repository.*;
import com.odontocare.installation.repository.InstallationProfileRepository;
import com.odontocare.shared.pagination.*;
import com.odontocare.shared.web.ApiException;
import java.time.*;
import java.util.*;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class AppointmentQueryService {
  private final AppointmentRepository appointments;
  private final AppointmentHistoryRepository history;
  private final InstallationProfileRepository profiles;
  private final com.odontocare.treatments.repository.TreatmentPlanningRepository planning;

  public AppointmentQueryService(
      AppointmentRepository appointments,
      AppointmentHistoryRepository history,
      InstallationProfileRepository profiles,
      com.odontocare.treatments.repository.TreatmentPlanningRepository planning) {
    this.appointments = appointments;
    this.history = history;
    this.profiles = profiles;
    this.planning = planning;
  }

  private ZoneId zone() {
    return ZoneId.of(profiles.findById((short) 1).orElseThrow().getTimeZone());
  }

  public AppointmentResponse get(UUID id) {
    return AppointmentResponse.of(
        appointments.findById(id).orElseThrow(ApiException::notFound), zone());
  }

  private Specification<Appointment> criteria(
      PageQuery pageQuery,
      UUID dentistId,
      UUID patientId,
      AppointmentStatus status,
      LocalDate from,
      LocalDate to,
      ZoneId zone) {
    if (from != null && to != null && to.isBefore(from))
      throw ApiException.badRequest("El rango de fechas no es válido.");
    Specification<Appointment> s =
        SearchSpecifications.text(
            pageQuery.getSearch(),
            "patient.fullName",
            "patient.code",
            "serviceName",
            "dentistName");
    if (dentistId != null)
      s =
          s.and(
              (root, criteriaQuery, builder) ->
                  builder.equal(root.get("dentist").get("id"), dentistId));
    if (patientId != null)
      s =
          s.and(
              (root, criteriaQuery, builder) ->
                  builder.equal(root.get("patient").get("id"), patientId));
    if (status != null) s = s.and(SearchSpecifications.equal("status", status));
    if (from != null)
      s =
          s.and(
              (root, criteriaQuery, builder) ->
                  builder.greaterThanOrEqualTo(
                      root.get("startsAt"), from.atStartOfDay(zone).toInstant()));
    if (to != null)
      s =
          s.and(
              (root, criteriaQuery, builder) ->
                  builder.lessThan(
                      root.get("startsAt"), to.plusDays(1).atStartOfDay(zone).toInstant()));
    return s;
  }

  public PageResponse<AppointmentResponse> list(
      PageQuery pageQuery,
      UUID dentistId,
      UUID patientId,
      AppointmentStatus status,
      LocalDate from,
      LocalDate to) {
    var zone = zone();
    return PageResponse.of(
        appointments
            .findAll(
                criteria(pageQuery, dentistId, patientId, status, from, to, zone),
                pageQuery.pageable(
                    Map.of(
                        "name",
                        "startsAt",
                        "startsAt",
                        "startsAt",
                        "patient",
                        "patient.fullName",
                        "status",
                        "status")))
            .map(appointment -> AppointmentResponse.of(appointment, zone)));
  }

  public record CalendarResponse(
      List<AppointmentResponse> items,
      String timeZone,
      LocalDate from,
      LocalDate to,
      List<com.odontocare.treatments.dto.ScheduledSessionContracts.Session> pendingSessions) {}

  public CalendarResponse calendar(UUID dentist, LocalDate from, LocalDate to) {
    return calendar(dentist, null, from, to);
  }

  public CalendarResponse calendar(UUID dentist, UUID patient, LocalDate from, LocalDate to) {
    if (to.isBefore(from) || java.time.temporal.ChronoUnit.DAYS.between(from, to) > 41)
      throw ApiException.badRequest("El calendario admite un intervalo máximo de 42 días.");
    var zone = zone();
    var page =
        appointments.findAll(
            criteria(new PageQuery(), dentist, patient, null, from, to, zone),
            PageRequest.of(0, 1000, Sort.by("startsAt").and(Sort.by("id"))));
    if (page.getTotalElements() > 1000)
      throw ApiException.badRequest(
          "Reduce el intervalo o selecciona un odontólogo para consultar el calendario.");
    return new CalendarResponse(
        page.getContent().stream()
            .map(appointment -> AppointmentResponse.of(appointment, zone))
            .toList(),
        zone.toString(),
        from,
        to,
        pending(dentist, patient, from, to));
  }

  private List<com.odontocare.treatments.dto.ScheduledSessionContracts.Session> pending(
      UUID dentist, UUID patient, LocalDate from, LocalDate to) {
    var query = new PageQuery();
    query.setSize(1000);
    var page = planning.list(patient, dentist, null, from, to, "PENDING", query);
    if (page.totalElements() > 1000)
      throw ApiException.badRequest("Reduce el intervalo para consultar las sesiones pendientes.");
    return page.items();
  }

  public PageResponse<HistoryResponse> history(UUID id, PageQuery pageQuery, String action) {
    if (!appointments.existsById(id)) throw ApiException.notFound();
    if (action != null
        && !Set.of("CREATED", "RESCHEDULED", "STATUS_CHANGED", "CANCELLED").contains(action))
      throw ApiException.badRequest("El filtro de movimiento no está permitido.");
    Specification<AppointmentHistory> s =
        SearchSpecifications.<AppointmentHistory>text(
                pageQuery.getSearch(), "reason", "actorName", "action")
            .and(
                (root, criteriaQuery, builder) ->
                    builder.equal(root.get("appointment").get("id"), id));
    s = s.and(SearchSpecifications.equal("action", action));
    return PageResponse.of(
        history
            .findAll(s, pageQuery.pageable(Map.of("name", "createdAt", "createdAt", "createdAt")))
            .map(HistoryResponse::of));
  }
}
