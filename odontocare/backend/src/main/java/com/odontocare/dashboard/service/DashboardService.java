package com.odontocare.dashboard.service;

import com.odontocare.audit.service.AuditService;
import com.odontocare.dashboard.dto.*;
import com.odontocare.dashboard.dto.DashboardContracts.Response;
import com.odontocare.dashboard.repository.DashboardRepository;
import com.odontocare.installation.repository.InstallationProfileRepository;
import com.odontocare.reports.dto.ReportContracts.*;
import com.odontocare.reports.dto.ReportQuery;
import com.odontocare.reports.model.ReportType;
import com.odontocare.reports.repository.ReportRepository;
import com.odontocare.reports.service.*;
import com.odontocare.shared.web.ApiException;
import java.time.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

@Service
public class DashboardService {
  private final DashboardRepository repository;
  private final ReportRepository reports;
  private final ReportAccess access;
  private final ReportWindowService windows;
  private final InstallationProfileRepository profiles;
  private final AuditService audit;

  public DashboardService(
      DashboardRepository repository,
      ReportRepository reports,
      ReportAccess access,
      ReportWindowService windows,
      InstallationProfileRepository profiles,
      AuditService audit) {
    this.repository = repository;
    this.reports = reports;
    this.access = access;
    this.windows = windows;
    this.profiles = profiles;
    this.audit = audit;
  }

  @Transactional(isolation = Isolation.REPEATABLE_READ)
  public Response get(DashboardQuery query) {
    var rangeQuery = new ReportQuery();
    rangeQuery.setFrom(query.getFrom());
    rangeQuery.setTo(query.getTo());
    var range = windows.resolve(rangeQuery);
    var today = windows.today(range.zone());
    var day = query.getDay() == null ? today : query.getDay();
    if (day.isBefore(LocalDate.of(1900, 1, 1)) || day.isAfter(LocalDate.of(2200, 12, 31)))
      throw ApiException.badRequest("Selecciona una fecha válida.");
    boolean finance = access.can("FINANCES_READ");
    var overdueQuery = new ReportQuery();
    overdueQuery.setStatus("OVERDUE");
    var overdue =
        finance ? reports.overdue(overdueQuery, new Window(today, today, range.zone())) : null;
    var balance = finance ? reports.table(ReportType.BALANCES, new ReportQuery(), range) : null;
    audit.record(
        "DASHBOARD_READ", "DASHBOARD", "overview", "Consultó panel operativo según permisos");
    return new Response(
        today,
        day,
        range.from(),
        range.to(),
        range.zone().getId(),
        windows.now(),
        access.can("APPOINTMENTS_READ")
            ? repository.appointments(new Window(day, day, range.zone()), query.getDentistId())
            : null,
        access.can("WHATSAPP_READ") ? repository.conversations() : null,
        finance
            ? repository.money(range, profiles.findById((short) 1).orElseThrow().getCurrency())
            : null,
        finance ? reports.metrics(overdue, reports.count(overdue)) : null,
        finance ? reports.metrics(balance, reports.count(balance)) : null);
  }
}
