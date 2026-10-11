package com.labcore.platform.dashboard;

import com.labcore.platform.client.ClientRepository;
import com.labcore.platform.client.ParticipantType;
import com.labcore.platform.dashboard.DashboardAnalyticsRepository.OperationalCounts;
import com.labcore.platform.security.ApplicationUser;
import com.labcore.platform.security.ApplicationUserRepository;
import com.labcore.platform.security.RoleCode;
import com.labcore.platform.workorders.WorkOrderStatus;
import com.labcore.platform.workorders.WorkOrderTest;
import com.labcore.platform.workorders.WorkOrderTestRepository;
import com.labcore.platform.workorders.WorkOrderTestStatus;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
class DashboardMetricService {
  private static final List<WorkOrderStatus> ACTIVE_WORK_ORDER_STATUSES =
      List.of(
          WorkOrderStatus.PENDING,
          WorkOrderStatus.IN_PROGRESS,
          WorkOrderStatus.RESULTS_RECORDED,
          WorkOrderStatus.VALIDATED);
  private final ClientRepository clientRepository;
  private final WorkOrderTestRepository workOrderTestRepository;
  private final ApplicationUserRepository userRepository;
  private final DashboardAnalyticsRepository analyticsRepository;
  private final DashboardRolePolicy rolePolicy;

  DashboardMetricService(
      ClientRepository clientRepository,
      WorkOrderTestRepository workOrderTestRepository,
      ApplicationUserRepository userRepository,
      DashboardAnalyticsRepository analyticsRepository,
      DashboardRolePolicy rolePolicy) {
    this.clientRepository = clientRepository;
    this.workOrderTestRepository = workOrderTestRepository;
    this.userRepository = userRepository;
    this.analyticsRepository = analyticsRepository;
    this.rolePolicy = rolePolicy;
  }

  List<DashboardDtos.Metric> metrics(
      ApplicationUser actor,
      DashboardScope scope,
      OperationalCounts counts,
      List<DashboardDtos.TimeSeriesPoint> activityTrend,
      List<DashboardDtos.StatusDistribution> workOrderStatuses) {
    List<DashboardDtos.Metric> metrics = new ArrayList<>();
    if (rolePolicy.hasAnyRole(actor, RoleCode.ADMIN)) {
      metrics.add(
          metric(
              "activePersonnel",
              "Personal activo",
              userRepository.countByActiveTrue(),
              "Usuarios habilitados",
              "/users",
              "Actual"));
    }
    if (rolePolicy.canViewCommercial(actor)) {
      addCommercialMetrics(metrics, actor, scope, counts);
    }
    if (rolePolicy.canViewTechnical(actor)) {
      addTechnicalMetrics(metrics, actor, scope, workOrderStatuses);
    }
    if (rolePolicy.isRestrictedAnalyst(actor)) {
      addAnalystMetrics(metrics, actor, scope, activityTrend);
    }
    if (rolePolicy.hasAnyRole(actor, RoleCode.ADMIN, RoleCode.VALIDATOR)) {
      metrics.add(
          metric(
              "validationQueue",
              "Por validar",
              analyticsRepository.validationQueue(scope),
              "Revisión técnica pendiente",
              "/validation",
              "Cola actual"));
    }
    if (rolePolicy.hasAnyRole(
        actor,
        RoleCode.ADMIN,
        RoleCode.COMMERCIAL,
        RoleCode.TECHNICAL_MANAGER,
        RoleCode.VALIDATOR)) {
      metrics.add(
          metric(
              "issuedReports",
              "Informes emitidos",
              analyticsRepository.issuedReports(scope),
              "Durante el período",
              "/reports?status=ISSUED&" + reportPeriodQuery(scope),
              periodLabel(scope)));
    }
    return metrics;
  }

  private void addCommercialMetrics(
      List<DashboardDtos.Metric> metrics,
      ApplicationUser actor,
      DashboardScope scope,
      OperationalCounts counts) {
    metrics.add(
        metric(
            "clients",
            "Clientes activos",
            clientRepository.countByActiveTrueAndParticipantTypeIn(
                List.of(ParticipantType.CLIENT, ParticipantType.BOTH)),
            "Solicitantes habilitados",
            "/clients?participantType=CLIENT",
            "Actual"));
    metrics.add(
        metric(
            "producers",
            "Productores activos",
            clientRepository.countByActiveTrueAndParticipantTypeIn(
                List.of(ParticipantType.PRODUCER, ParticipantType.BOTH)),
            "Propietarios y productores",
            "/clients?participantType=PRODUCER",
            "Actual"));
    metrics.add(
        metric(
            "receivedOrders",
            "OS recibidas",
            counts.serviceOrders(),
            "Durante el período",
            "/orders?" + periodQuery(scope),
            periodLabel(scope)));
    metrics.add(
        metric(
            "receivedSamples",
            "Muestras recibidas",
            counts.samples(),
            "Durante el período",
            "/orders?" + periodQuery(scope),
            periodLabel(scope)));
    metrics.add(
        metric(
            "activeServices",
            "Servicios activos",
            analyticsRepository.activeServices(scope.laboratoryAreaId()),
            "Oferta técnica vigente",
            rolePolicy.hasAnyRole(actor, RoleCode.ADMIN) ? "/catalogs?tab=services" : "/orders",
            "Actual"));
  }

  private void addTechnicalMetrics(
      List<DashboardDtos.Metric> metrics,
      ApplicationUser actor,
      DashboardScope scope,
      List<DashboardDtos.StatusDistribution> workOrderStatuses) {
    long activeTechnicalOrders =
        workOrderStatuses.stream()
            .filter(
                item ->
                    ACTIVE_WORK_ORDER_STATUSES.stream()
                        .anyMatch(status -> status.name().equals(item.status())))
            .mapToLong(DashboardDtos.StatusDistribution::value)
            .sum();
    metrics.add(
        metric(
            "technicalOrders",
            rolePolicy.isRestrictedAnalyst(actor) ? "Mis OT del período" : "OT activas del período",
            activeTechnicalOrders,
            rolePolicy.isRestrictedAnalyst(actor) ? "Trabajo asignado" : "Carga técnica abierta",
            "/work-orders?" + periodQuery(scope),
            periodLabel(scope)));
    metrics.add(
        metric(
            "pendingResults",
            "Resultados pendientes",
            analyticsRepository.pendingResults(
                rolePolicy.isRestrictedAnalyst(actor)
                    ? new DashboardScope(
                        scope.from(),
                        scope.to(),
                        scope.fromInclusive(),
                        scope.toExclusive(),
                        scope.laboratoryAreaId(),
                        actor.getId(),
                        scope.timeZone())
                    : scope),
            rolePolicy.isRestrictedAnalyst(actor) ? "Asignados a tu usuario" : "Operación técnica",
            "/results",
            "Cola actual"));
  }

  private void addAnalystMetrics(
      List<DashboardDtos.Metric> metrics,
      ApplicationUser actor,
      DashboardScope scope,
      List<DashboardDtos.TimeSeriesPoint> activityTrend) {
    List<WorkOrderTest> assignments =
        workOrderTestRepository.findByAssignedAnalystId(actor.getId());
    metrics.add(
        metric(
            "assignedTests",
            "Ensayos asignados",
            assignments.size(),
            "Carga personal total",
            "/results",
            "Actual"));
    metrics.add(
        metric(
            "corrections",
            "Correcciones solicitadas",
            assignments.stream()
                .filter(test -> test.getStatus() == WorkOrderTestStatus.CORRECTION_REQUESTED)
                .count(),
            "Requieren atención",
            "/results",
            "Actual"));
    metrics.add(
        metric(
            "recordedResults",
            "Resultados registrados",
            activityTrend.stream().mapToLong(DashboardDtos.TimeSeriesPoint::value).sum(),
            "Durante el período",
            "/results",
            periodLabel(scope)));
  }

  private DashboardDtos.Metric metric(
      String key, String label, long value, String context, String path, String trend) {
    return new DashboardDtos.Metric(key, label, value, context, path, trend);
  }

  private String periodQuery(DashboardScope scope) {
    String query = "createdFrom=" + scope.from() + "&createdTo=" + scope.to();
    return scope.laboratoryAreaId() == null
        ? query
        : query + "&laboratoryAreaId=" + scope.laboratoryAreaId();
  }

  private String periodLabel(DashboardScope scope) {
    long days = ChronoUnit.DAYS.between(scope.from(), scope.to()) + 1;
    return days + (days == 1 ? " día" : " días");
  }

  private String reportPeriodQuery(DashboardScope scope) {
    String query = "issuedFrom=" + scope.fromInclusive() + "&issuedTo=" + scope.toExclusive();
    return scope.laboratoryAreaId() == null
        ? query
        : query + "&laboratoryAreaId=" + scope.laboratoryAreaId();
  }
}
