package com.labcore.platform.dashboard;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

import com.labcore.platform.catalog.LaboratoryAreaRepository;
import com.labcore.platform.common.exception.BusinessRuleViolationException;
import com.labcore.platform.dashboard.DashboardAnalyticsRepository.OperationalCounts;
import com.labcore.platform.organization.OrganizationProfileService;
import com.labcore.platform.security.ApplicationRole;
import com.labcore.platform.security.ApplicationUser;
import com.labcore.platform.security.ApplicationUserRepository;
import com.labcore.platform.security.RoleCode;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DashboardServiceTest {
  @Mock private ApplicationUserRepository userRepository;
  @Mock private LaboratoryAreaRepository areaRepository;
  @Mock private OrganizationProfileService organizationService;
  @Mock private DashboardAnalyticsRepository analyticsRepository;
  @Mock private DashboardMetricService metricService;
  @Mock private DashboardAlertService alertService;

  private DashboardService service;

  @BeforeEach
  void setUp() {
    service =
        new DashboardService(
            userRepository,
            areaRepository,
            organizationService,
            analyticsRepository,
            new DashboardRolePolicy(),
            metricService,
            alertService);
    given(organizationService.zoneId()).willReturn(ZoneId.of("America/Lima"));
  }

  @Test
  void restrictsAnAnalystDashboardToTheirOwnAssignments() {
    ApplicationUser analyst = user(RoleCode.ANALYST);
    given(userRepository.findByEmailIgnoreCase(analyst.getEmail()))
        .willReturn(Optional.of(analyst));
    given(analyticsRepository.operationalCounts(any())).willReturn(new OperationalCounts(0, 0));
    given(analyticsRepository.analystResultTrend(any())).willReturn(List.of());

    DashboardDtos.Summary summary =
        service.getSummary(
            analyst.getEmail(), LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 15), null);

    ArgumentCaptor<DashboardScope> scopeCaptor = ArgumentCaptor.forClass(DashboardScope.class);
    org.mockito.Mockito.verify(analyticsRepository).analystResultTrend(scopeCaptor.capture());
    assertThat(scopeCaptor.getValue().analystId()).isEqualTo(analyst.getId());
    assertThat(summary.serviceOrderStatuses()).isEmpty();
    assertThat(summary.activityTrend()).hasSize(15);
    assertThat(summary.activityTrendLabel()).isEqualTo("Resultados registrados por día");
  }

  @Test
  void combinesCommercialVisibilityWithAnAnalystsPersonalTechnicalScope() {
    ApplicationUser actor = user(RoleCode.COMMERCIAL, RoleCode.ANALYST);
    given(userRepository.findByEmailIgnoreCase(actor.getEmail())).willReturn(Optional.of(actor));
    given(analyticsRepository.operationalCounts(any())).willReturn(new OperationalCounts(2, 3));
    given(analyticsRepository.receivedSamplesTrend(any())).willReturn(List.of());
    given(analyticsRepository.analystResultTrend(any())).willReturn(List.of());

    DashboardDtos.Summary summary =
        service.getSummary(
            actor.getEmail(), LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 15), null);

    ArgumentCaptor<DashboardScope> commercialScope = ArgumentCaptor.forClass(DashboardScope.class);
    ArgumentCaptor<DashboardScope> analystScope = ArgumentCaptor.forClass(DashboardScope.class);
    org.mockito.Mockito.verify(analyticsRepository).receivedSamplesTrend(commercialScope.capture());
    org.mockito.Mockito.verify(analyticsRepository).analystResultTrend(analystScope.capture());
    assertThat(commercialScope.getValue().analystId()).isNull();
    assertThat(analystScope.getValue().analystId()).isEqualTo(actor.getId());
    assertThat(summary.serviceOrderStatuses()).isNotEmpty();
    assertThat(summary.workOrderStatuses()).isNotEmpty();
    assertThat(summary.activityTrendLabel()).isEqualTo("Muestras recibidas por día");
  }

  @Test
  void rejectsAnInvertedDateRange() {
    ApplicationUser administrator = user(RoleCode.ADMIN);
    given(userRepository.findByEmailIgnoreCase(administrator.getEmail()))
        .willReturn(Optional.of(administrator));

    assertThatThrownBy(
            () ->
                service.getSummary(
                    administrator.getEmail(),
                    LocalDate.of(2026, 9, 15),
                    LocalDate.of(2026, 9, 1),
                    null))
        .isInstanceOf(BusinessRuleViolationException.class)
        .hasMessage("La fecha inicial no puede ser posterior a la final.");
  }

  private ApplicationUser user(RoleCode... roles) {
    return new ApplicationUser(
        "Usuario de prueba",
        roles[0].name().toLowerCase() + "@example.test",
        "hash",
        java.util.Arrays.stream(roles)
            .map(role -> new ApplicationRole(role, role.name()))
            .collect(java.util.stream.Collectors.toSet()));
  }
}
