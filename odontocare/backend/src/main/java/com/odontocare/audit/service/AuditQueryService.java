package com.odontocare.audit.service;

import com.odontocare.audit.dto.AuditResponse;
import com.odontocare.audit.model.AuditEvent;
import com.odontocare.audit.repository.AuditEventRepository;
import com.odontocare.installation.repository.InstallationProfileRepository;
import com.odontocare.shared.pagination.*;
import com.odontocare.shared.web.ApiException;
import java.time.*;
import java.util.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuditQueryService {
  private final AuditEventRepository events;
  private final InstallationProfileRepository profiles;

  public AuditQueryService(AuditEventRepository events, InstallationProfileRepository profiles) {
    this.events = events;
    this.profiles = profiles;
  }

  @Transactional(readOnly = true)
  public PageResponse<AuditResponse> list(
      PageQuery query, String entityType, String action, LocalDate fromDate, LocalDate toDate) {
    if (fromDate != null && toDate != null && fromDate.isAfter(toDate))
      throw ApiException.badRequest("El rango de fechas no es válido.");
    Specification<AuditEvent> spec =
        SearchSpecifications.<AuditEvent>text(
                query.getSearch(), "actorName", "summary", "action", "entityId")
            .and(SearchSpecifications.equal("entityType", entityType))
            .and(SearchSpecifications.equal("action", action));
    spec =
        spec.and(
            (r, q, b) ->
                b.and(
                    b.not(r.get("action").in(AuditPolicy.OMITTED)),
                    b.notLike(r.get("action"), "%!_READ", '!')));
    ZoneId zone = ZoneId.of(profiles.findById((short) 1).orElseThrow().getTimeZone());
    if (fromDate != null)
      spec =
          spec.and(
              (root, q, cb) ->
                  cb.greaterThanOrEqualTo(
                      root.get("occurredAt"), fromDate.atStartOfDay(zone).toInstant()));
    if (toDate != null)
      spec =
          spec.and(
              (root, q, cb) ->
                  cb.lessThan(
                      root.get("occurredAt"), toDate.plusDays(1).atStartOfDay(zone).toInstant()));
    return PageResponse.of(
        events
            .findAll(
                spec,
                query.pageable(
                    Map.of(
                        "name",
                        "occurredAt",
                        "date",
                        "occurredAt",
                        "actor",
                        "actorName",
                        "action",
                        "action")))
            .map(
                event ->
                    new AuditResponse(
                        event.getId(),
                        event.getActorId(),
                        event.getActorName(),
                        event.getAction(),
                        event.getEntityType(),
                        event.getEntityId(),
                        event.getSummary(),
                        event.getRequestId(),
                        event.getOccurredAt())));
  }
}
