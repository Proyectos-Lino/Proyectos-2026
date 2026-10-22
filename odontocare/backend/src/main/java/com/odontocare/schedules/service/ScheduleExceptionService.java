package com.odontocare.schedules.service;

import com.odontocare.audit.service.AuditService;
import com.odontocare.dentists.repository.DentistRepository;
import com.odontocare.installation.service.ConfigurationLock;
import com.odontocare.schedules.dto.*;
import com.odontocare.schedules.model.*;
import com.odontocare.schedules.repository.ScheduleExceptionRepository;
import com.odontocare.shared.pagination.*;
import com.odontocare.shared.web.ApiException;
import java.time.LocalDate;
import java.util.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ScheduleExceptionService {
  private final ScheduleExceptionRepository exceptions;
  private final DentistRepository dentists;
  private final ConfigurationLock lock;
  private final AuditService audit;

  public ScheduleExceptionService(
      ScheduleExceptionRepository exceptions,
      DentistRepository dentists,
      ConfigurationLock lock,
      AuditService audit) {
    this.exceptions = exceptions;
    this.dentists = dentists;
    this.lock = lock;
    this.audit = audit;
  }

  @Transactional(readOnly = true)
  public PageResponse<ExceptionResponse> list(
      PageQuery query,
      Boolean active,
      UUID dentistId,
      ExceptionKind kind,
      LocalDate fromDate,
      LocalDate toDate) {
    if (fromDate != null && toDate != null && fromDate.isAfter(toDate))
      throw ApiException.badRequest("El rango de fechas no es válido.");
    Specification<ScheduleException> spec =
        SearchSpecifications.<ScheduleException>text(query.getSearch(), "reason")
            .and(SearchSpecifications.equal("active", active))
            .and(SearchSpecifications.equal("kind", kind));
    if (dentistId != null)
      spec = spec.and((root, q, cb) -> cb.equal(root.get("dentist").get("id"), dentistId));
    if (fromDate != null)
      spec = spec.and((root, q, cb) -> cb.greaterThanOrEqualTo(root.get("endDate"), fromDate));
    if (toDate != null)
      spec = spec.and((root, q, cb) -> cb.lessThanOrEqualTo(root.get("startDate"), toDate));
    return PageResponse.of(
        exceptions
            .findAll(
                spec,
                query.pageable(
                    Map.of(
                        "name", "startDate", "startDate", "startDate", "createdAt", "createdAt")))
            .map(this::response));
  }

  @Transactional
  public ExceptionResponse create(ExceptionRequest request) {
    lock.acquire();
    var exception = new ScheduleException();
    apply(exception, request);
    exceptions.saveAndFlush(exception);
    audit.record(
        "EXCEPTION_CREATED",
        "EXCEPTION",
        exception.getId(),
        "Añadió un bloqueo de disponibilidad: " + exception.getReason());
    return response(exception);
  }

  @Transactional
  public ExceptionResponse update(UUID id, ExceptionRequest request) {
    lock.acquire();
    var exception = exceptions.findById(id).orElseThrow(ApiException::notFound);
    exception.checkVersion(request.version());
    apply(exception, request);
    exceptions.saveAndFlush(exception);
    audit.record(
        "EXCEPTION_UPDATED",
        "EXCEPTION",
        id,
        "Actualizó fechas y estado del bloqueo: " + exception.getReason());
    return response(exception);
  }

  private void apply(ScheduleException exception, ExceptionRequest request) {
    if (request.endDate().isBefore(request.startDate()))
      throw ApiException.badRequest("La fecha de fin debe ser igual o posterior al inicio.");
    if (request.kind() == ExceptionKind.ABSENCE && request.dentistId() == null)
      throw ApiException.badRequest("Una ausencia debe pertenecer a un odontólogo.");
    if (request.startMinute() != null || request.endMinute() != null) {
      if (request.startMinute() == null
          || request.endMinute() == null
          || !request.startDate().equals(request.endDate())
          || request.endMinute() <= request.startMinute()) {
        throw ApiException.badRequest(
            "Un bloqueo parcial necesita un solo día y horas de inicio y fin válidas.");
      }
    }
    var dentist =
        request.dentistId() == null
            ? null
            : dentists
                .findById(request.dentistId())
                .orElseThrow(() -> ApiException.badRequest("El odontólogo no existe."));
    exception.setDentist(dentist);
    exception.setKind(request.kind());
    exception.setStartDate(request.startDate());
    exception.setEndDate(request.endDate());
    exception.setStartMinute(request.startMinute());
    exception.setEndMinute(request.endMinute());
    exception.setReason(request.reason().strip());
    exception.setActive(request.active());
  }

  private ExceptionResponse response(ScheduleException exception) {
    var dentist = exception.getDentist();
    return new ExceptionResponse(
        exception.getId(),
        dentist == null ? null : dentist.getId(),
        dentist == null ? "Todo el consultorio" : dentist.getFullName(),
        exception.getKind(),
        exception.getStartDate(),
        exception.getEndDate(),
        exception.getStartMinute(),
        exception.getEndMinute(),
        exception.getReason(),
        exception.getActive(),
        exception.getVersion());
  }
}
