package com.odontocare.schedules.service;

import com.odontocare.audit.service.AuditService;
import com.odontocare.dentists.repository.DentistRepository;
import com.odontocare.installation.service.ConfigurationLock;
import com.odontocare.schedules.dto.*;
import com.odontocare.schedules.model.*;
import com.odontocare.schedules.repository.WeeklyPeriodRepository;
import com.odontocare.shared.pagination.*;
import com.odontocare.shared.web.ApiException;
import java.util.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WeeklyPeriodService {
  private final WeeklyPeriodRepository periods;
  private final DentistRepository dentists;
  private final ConfigurationLock lock;
  private final AuditService audit;

  public WeeklyPeriodService(
      WeeklyPeriodRepository periods,
      DentistRepository dentists,
      ConfigurationLock lock,
      AuditService audit) {
    this.periods = periods;
    this.dentists = dentists;
    this.lock = lock;
    this.audit = audit;
  }

  @Transactional(readOnly = true)
  public PageResponse<PeriodResponse> list(
      PageQuery query, Boolean active, UUID dentistId, Integer dayOfWeek, PeriodKind kind) {
    Specification<WeeklyPeriod> spec =
        SearchSpecifications.<WeeklyPeriod>equal("active", active)
            .and(SearchSpecifications.equal("dayOfWeek", dayOfWeek))
            .and(SearchSpecifications.equal("kind", kind));
    if (dentistId != null)
      spec = spec.and((root, q, cb) -> cb.equal(root.get("dentist").get("id"), dentistId));
    spec = spec.and(SearchSpecifications.text(query.getSearch(), "dentist.fullName"));
    return PageResponse.of(
        periods
            .findAll(
                spec,
                query.pageable(
                    Map.of(
                        "name",
                        "dayOfWeek",
                        "day",
                        "dayOfWeek",
                        "start",
                        "startMinute",
                        "createdAt",
                        "createdAt")))
            .map(this::response));
  }

  @Transactional
  public PeriodResponse create(PeriodRequest request) {
    lock.acquire();
    var period = new WeeklyPeriod();
    apply(period, request);
    validateDay(period, null);
    periods.saveAndFlush(period);
    audit.record(
        "PERIOD_CREATED",
        "PERIOD",
        period.getId(),
        "Añadió " + period.getKind() + " a la jornada de " + period.getDentist().getFullName());
    return response(period);
  }

  @Transactional
  public PeriodResponse update(UUID id, PeriodRequest request) {
    lock.acquire();
    var period = periods.findById(id).orElseThrow(ApiException::notFound);
    period.checkVersion(request.version());
    if (!period.getDentist().getId().equals(request.dentistId())
        || period.getDayOfWeek() != request.dayOfWeek()) {
      throw ApiException.badRequest(
          "El odontólogo y el día de un intervalo no se cambian. Desactívalo y crea otro.");
    }
    apply(period, request);
    validateDay(period, id);
    periods.saveAndFlush(period);
    audit.record(
        "PERIOD_UPDATED",
        "PERIOD",
        id,
        "Actualizó horario y estado del intervalo de " + period.getDentist().getFullName());
    return response(period);
  }

  private void apply(WeeklyPeriod period, PeriodRequest request) {
    if (request.endMinute() <= request.startMinute())
      throw ApiException.badRequest("La hora de fin debe ser posterior al inicio.");
    var dentist =
        dentists
            .findById(request.dentistId())
            .orElseThrow(() -> ApiException.badRequest("Selecciona un odontólogo existente."));
    if (request.active() && !dentist.getActive())
      throw ApiException.badRequest("Activa al odontólogo antes de habilitar su horario.");
    period.setDentist(dentist);
    period.setDayOfWeek(request.dayOfWeek());
    period.setKind(request.kind());
    period.setStartMinute(request.startMinute());
    period.setEndMinute(request.endMinute());
    period.setActive(request.active());
  }

  private void validateDay(WeeklyPeriod changed, UUID excluded) {
    var current =
        new ArrayList<>(
            periods.findByDentistIdAndDayOfWeekAndActiveTrue(
                changed.getDentist().getId(), changed.getDayOfWeek()));
    current.removeIf(period -> period.getId() != null && period.getId().equals(excluded));
    if (changed.getActive()) current.add(changed);
    PeriodValidator.validate(current);
  }

  private PeriodResponse response(WeeklyPeriod period) {
    return new PeriodResponse(
        period.getId(),
        period.getDentist().getId(),
        period.getDentist().getFullName(),
        period.getDayOfWeek(),
        period.getKind(),
        period.getStartMinute(),
        period.getEndMinute(),
        period.getActive(),
        period.getVersion());
  }
}
