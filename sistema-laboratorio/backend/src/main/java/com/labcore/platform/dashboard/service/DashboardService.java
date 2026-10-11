package com.labcore.platform.dashboard;

import com.labcore.platform.catalog.LaboratoryAreaRepository;
import com.labcore.platform.common.exception.BusinessRuleViolationException;
import com.labcore.platform.common.exception.ResourceNotFoundException;
import com.labcore.platform.dashboard.DashboardAnalyticsRepository.DistributionDimension;
import com.labcore.platform.dashboard.DashboardAnalyticsRepository.OperationalCounts;
import com.labcore.platform.organization.OrganizationProfileService;
import com.labcore.platform.security.ApplicationUser;
import com.labcore.platform.security.ApplicationUserRepository;
import com.labcore.platform.security.RoleCode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class DashboardService {
  private static final int DEFAULT_PERIOD_DAYS = 30;
  private static final int MAXIMUM_PERIOD_DAYS = 366;

  private final ApplicationUserRepository userRepository;
  private final LaboratoryAreaRepository areaRepository;
  private final OrganizationProfileService organizationService;
  private final DashboardAnalyticsRepository analyticsRepository;
  private final DashboardRolePolicy rolePolicy;
  private final DashboardMetricService metricService;
  private final DashboardAlertService alertService;

  public DashboardService(
      ApplicationUserRepository userRepository,
      LaboratoryAreaRepository areaRepository,
      OrganizationProfileService organizationService,
      DashboardAnalyticsRepository analyticsRepository,
      DashboardRolePolicy rolePolicy,
      DashboardMetricService metricService,
      DashboardAlertService alertService) {
    this.userRepository = userRepository;
    this.areaRepository = areaRepository;
    this.organizationService = organizationService;
    this.analyticsRepository = analyticsRepository;
    this.rolePolicy = rolePolicy;
    this.metricService = metricService;
    this.alertService = alertService;
  }

  public DashboardDtos.Summary getSummary(
      String actorEmail, LocalDate requestedFrom, LocalDate requestedTo, UUID laboratoryAreaId) {
    ApplicationUser actor = currentUser(actorEmail);
    DashboardScope scope = scope(requestedFrom, requestedTo, laboratoryAreaId);
    DashboardScope technicalScope = technicalScope(actor, scope);
    OperationalCounts counts = analyticsRepository.operationalCounts(scope);
    List<DashboardDtos.TimeSeriesPoint> analystTrend = analystActivityTrend(actor, technicalScope);
    List<DashboardDtos.TimeSeriesPoint> activityTrend =
        rolePolicy.isPureAnalyst(actor) ? analystTrend : receivedSamplesTrend(scope);
    List<DashboardDtos.StatusDistribution> serviceOrderStatuses =
        rolePolicy.canViewCommercial(actor) ? serviceOrderDistribution(scope) : List.of();
    List<DashboardDtos.StatusDistribution> workOrderStatuses =
        rolePolicy.canViewTechnical(actor) ? workOrderDistribution(technicalScope) : List.of();
    DashboardScope overviewScope = rolePolicy.isPureAnalyst(actor) ? technicalScope : scope;

    return new DashboardDtos.Summary(
        new DashboardDtos.DashboardPeriod(scope.from(), scope.to(), scope.laboratoryAreaId()),
        areaRepository.findByActiveTrueOrderByNameAsc().stream()
            .map(area -> new DashboardDtos.FilterOption(area.getId(), area.getName()))
            .toList(),
        metricService.metrics(actor, scope, counts, analystTrend, workOrderStatuses),
        serviceOrderStatuses,
        workOrderStatuses,
        activityTrend,
        rolePolicy.isPureAnalyst(actor)
            ? "Resultados registrados por día"
            : "Muestras recibidas por día",
        topServices(actor, overviewScope),
        distributions(actor, overviewScope),
        authorizedCycleTimes(actor, scope, technicalScope),
        alertService.alerts(actor, scope.laboratoryAreaId()),
        Instant.now());
  }

  private List<DashboardDtos.RankingItem> topServices(ApplicationUser actor, DashboardScope scope) {
    String path =
        rolePolicy.hasAnyRole(actor, RoleCode.ADMIN)
            ? "/catalogs?tab=services"
            : rolePolicy.canViewCommercial(actor)
                ? "/orders"
                : rolePolicy.isRestrictedAnalyst(actor) ? "/results" : "/work-orders";
    return analyticsRepository.topServices(scope).stream()
        .map(
            item ->
                new DashboardDtos.RankingItem(
                    item.id(), item.label(), item.value(), item.context(), path))
        .toList();
  }

  private List<DashboardDtos.CycleTime> authorizedCycleTimes(
      ApplicationUser actor, DashboardScope scope, DashboardScope technicalScope) {
    if (rolePolicy.canViewFullTechnicalAnalytics(actor)) {
      return analyticsRepository.cycleTimes(scope);
    }

    boolean commercial = rolePolicy.canViewCommercial(actor);
    boolean analyst = rolePolicy.isRestrictedAnalyst(actor);
    List<DashboardDtos.CycleTime> result = new ArrayList<>();
    if (commercial) {
      result.addAll(
          analyticsRepository.cycleTimes(scope).stream()
              .filter(
                  cycle ->
                      cycle.key().equals("receptionToWorkOrder")
                          || cycle.key().equals("validationToReport"))
              .toList());
    }
    if (analyst) {
      result.addAll(
          analyticsRepository.cycleTimes(technicalScope).stream()
              .filter(
                  cycle ->
                      cycle.key().equals("workOrderToEntry")
                          || cycle.key().equals("entryToValidation"))
              .toList());
    }
    return result;
  }

  private DashboardScope technicalScope(ApplicationUser actor, DashboardScope scope) {
    if (!rolePolicy.isRestrictedAnalyst(actor)) {
      return scope;
    }
    return new DashboardScope(
        scope.from(),
        scope.to(),
        scope.fromInclusive(),
        scope.toExclusive(),
        scope.laboratoryAreaId(),
        actor.getId(),
        scope.timeZone());
  }

  private DashboardScope scope(LocalDate requestedFrom, LocalDate requestedTo, UUID areaId) {
    ZoneId zoneId = organizationService.zoneId();
    LocalDate today = LocalDate.now(zoneId);
    LocalDate to = requestedTo == null ? today : requestedTo;
    LocalDate from = requestedFrom == null ? to.minusDays(DEFAULT_PERIOD_DAYS - 1L) : requestedFrom;
    if (from.isAfter(to)) {
      throw new BusinessRuleViolationException(
          "La fecha inicial no puede ser posterior a la final.");
    }
    if (ChronoUnit.DAYS.between(from, to) >= MAXIMUM_PERIOD_DAYS) {
      throw new BusinessRuleViolationException("El período del dashboard no puede superar un año.");
    }
    if (areaId != null && !areaRepository.existsById(areaId)) {
      throw new ResourceNotFoundException("el área de laboratorio", areaId);
    }
    return new DashboardScope(
        from,
        to,
        from.atStartOfDay(zoneId).toInstant(),
        to.plusDays(1).atStartOfDay(zoneId).toInstant(),
        areaId,
        null,
        zoneId.getId());
  }

  private List<DashboardDtos.Distribution> distributions(
      ApplicationUser actor, DashboardScope scope) {
    List<DashboardDtos.Distribution> distributions = new ArrayList<>();
    distributions.add(
        distribution(
            "laboratory",
            "Muestras por laboratorio",
            analyticsRepository.distribution(scope, DistributionDimension.LABORATORY)));
    distributions.add(
        distribution(
            "sampleType",
            "Muestras por tipo",
            analyticsRepository.distribution(scope, DistributionDimension.SAMPLE_TYPE)));
    distributions.add(
        distribution(
            "service",
            "Solicitudes por servicio",
            analyticsRepository.distribution(scope, DistributionDimension.SERVICE)));
    if (rolePolicy.canViewCommercial(actor)) {
      distributions.add(
          distribution(
              "serviceOrderStatus",
              "OS por estado",
              translateServiceOrderStatuses(
                  analyticsRepository.distribution(
                      scope, DistributionDimension.SERVICE_ORDER_STATUS))));
    } else {
      distributions.add(
          distribution(
              "workOrderStatus",
              "OT por estado",
              translateWorkOrderStatuses(
                  analyticsRepository.distribution(
                      scope, DistributionDimension.WORK_ORDER_STATUS))));
    }
    return distributions;
  }

  private DashboardDtos.Distribution distribution(
      String key, String title, List<DashboardDtos.DistributionItem> items) {
    return new DashboardDtos.Distribution(key, title, items);
  }

  private List<DashboardDtos.DistributionItem> translateServiceOrderStatuses(
      List<DashboardDtos.DistributionItem> items) {
    return translated(
        items,
        Map.of(
            "DRAFT", "Borrador",
            "IN_PROGRESS", "En proceso",
            "FINALIZED", "Finalizada",
            "CANCELLED", "Anulada"));
  }

  private List<DashboardDtos.DistributionItem> translateWorkOrderStatuses(
      List<DashboardDtos.DistributionItem> items) {
    return translated(
        items,
        Map.of(
            "PENDING", "Pendiente",
            "IN_PROGRESS", "En proceso",
            "RESULTS_RECORDED", "Resultados registrados",
            "VALIDATED", "Validada",
            "CLOSED", "Cerrada",
            "CANCELLED", "Anulada"));
  }

  private List<DashboardDtos.DistributionItem> translated(
      List<DashboardDtos.DistributionItem> items, Map<String, String> labels) {
    return items.stream()
        .map(
            item ->
                new DashboardDtos.DistributionItem(
                    item.key(), labels.getOrDefault(item.key(), item.label()), item.value()))
        .toList();
  }

  private List<DashboardDtos.TimeSeriesPoint> analystActivityTrend(
      ApplicationUser actor, DashboardScope technicalScope) {
    if (!rolePolicy.isRestrictedAnalyst(actor)) {
      return List.of();
    }
    return fillTrend(technicalScope, analyticsRepository.analystResultTrend(technicalScope));
  }

  private List<DashboardDtos.TimeSeriesPoint> receivedSamplesTrend(DashboardScope scope) {
    return fillTrend(scope, analyticsRepository.receivedSamplesTrend(scope));
  }

  private List<DashboardDtos.TimeSeriesPoint> fillTrend(
      DashboardScope scope, List<DashboardDtos.TimeSeriesPoint> stored) {
    Map<LocalDate, Long> values =
        stored.stream()
            .collect(
                Collectors.toMap(
                    DashboardDtos.TimeSeriesPoint::date, DashboardDtos.TimeSeriesPoint::value));
    return scope
        .from()
        .datesUntil(scope.to().plusDays(1))
        .map(date -> new DashboardDtos.TimeSeriesPoint(date, values.getOrDefault(date, 0L)))
        .toList();
  }

  private List<DashboardDtos.StatusDistribution> serviceOrderDistribution(DashboardScope scope) {
    Map<String, Long> counts = statusCounts(scope, DistributionDimension.SERVICE_ORDER_STATUS);
    return List.of(
        status("DRAFT", "Borrador", counts),
        status("IN_PROGRESS", "En proceso", counts),
        status("FINALIZED", "Finalizada", counts),
        status("CANCELLED", "Anulada", counts));
  }

  private List<DashboardDtos.StatusDistribution> workOrderDistribution(DashboardScope scope) {
    Map<String, Long> counts = statusCounts(scope, DistributionDimension.WORK_ORDER_STATUS);
    return List.of(
        status("PENDING", "Pendiente", counts),
        status("IN_PROGRESS", "En proceso", counts),
        status("RESULTS_RECORDED", "Resultados registrados", counts),
        status("VALIDATED", "Validada", counts),
        status("CLOSED", "Cerrada", counts),
        status("CANCELLED", "Anulada", counts));
  }

  private Map<String, Long> statusCounts(DashboardScope scope, DistributionDimension dimension) {
    return analyticsRepository.distribution(scope, dimension).stream()
        .collect(
            Collectors.toMap(
                DashboardDtos.DistributionItem::key, DashboardDtos.DistributionItem::value));
  }

  private DashboardDtos.StatusDistribution status(
      String code, String label, Map<String, Long> counts) {
    return new DashboardDtos.StatusDistribution(code, label, counts.getOrDefault(code, 0L));
  }

  private ApplicationUser currentUser(String email) {
    ApplicationUser user =
        userRepository
            .findByEmailIgnoreCase(email)
            .orElseThrow(
                () -> new ResourceNotFoundException("el usuario autenticado", UUID.randomUUID()));
    if (!user.isActive()) {
      throw new BusinessRuleViolationException("El usuario autenticado se encuentra desactivado.");
    }
    return user;
  }
}
