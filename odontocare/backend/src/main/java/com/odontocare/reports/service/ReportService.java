package com.odontocare.reports.service;

import com.odontocare.audit.service.AuditService;
import com.odontocare.installation.repository.InstallationProfileRepository;
import com.odontocare.reports.dto.ReportContracts.*;
import com.odontocare.reports.dto.ReportQuery;
import com.odontocare.reports.model.ReportType;
import com.odontocare.reports.repository.ReportRepository;
import com.odontocare.shared.web.ApiException;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

@Service
public class ReportService {
  private final ReportRepository repository;
  private final ReportAccess access;
  private final ReportDefinitions definitions;
  private final ReportWindowService windows;
  private final InstallationProfileRepository profiles;
  private final AuditService audit;

  public ReportService(
      ReportRepository repository,
      ReportAccess access,
      ReportDefinitions definitions,
      ReportWindowService windows,
      InstallationProfileRepository profiles,
      AuditService audit) {
    this.repository = repository;
    this.access = access;
    this.definitions = definitions;
    this.windows = windows;
    this.profiles = profiles;
    this.audit = audit;
  }

  @Transactional(readOnly = true)
  public Catalog catalog() {
    var available =
        Arrays.stream(ReportType.values())
            .filter(t -> access.can(t.permission()))
            .map(definitions::get)
            .toList();
    var currencies = new TreeSet<String>();
    currencies.add(profiles.findById((short) 1).orElseThrow().getCurrency());
    if (access.can("FINANCES_READ")) currencies.addAll(repository.currencies());
    return new Catalog(available, List.copyOf(currencies), access.can("REPORTS_EXPORT"));
  }

  public void validate(ReportType type, ReportQuery query) {
    if (query.isIncludeEarlier() && type != ReportType.INSTALLMENTS)
      throw ApiException.badRequest(
          "Incluir vencimientos anteriores solo corresponde al reporte de cuotas.");
    var d = definitions.get(type);
    if ((!d.dentist() && query.getDentistId() != null)
        || (!d.patient() && query.getPatientId() != null)
        || (!d.currency() && query.getCurrency() != null)
        || (!d.method() && query.getMethod() != null))
      throw ApiException.badRequest("Este filtro no corresponde al reporte seleccionado.");
    if (query.getStatus() != null && !d.statuses().containsKey(query.getStatus()))
      throw ApiException.badRequest("Selecciona un estado válido para este reporte.");
    String sort = query.getSort().equals("name") ? d.defaultSort() : query.getSort();
    if (d.columns().stream().noneMatch(c -> c.key().equals(sort)))
      throw ApiException.badRequest("El campo de ordenación no está permitido.");
  }

  @Transactional(isolation = Isolation.REPEATABLE_READ)
  public Page page(ReportType type, ReportQuery query) {
    access.require(type, false);
    validate(type, query);
    var window = windows.resolve(query);
    var table = repository.table(type, query, window);
    long total = repository.count(table);
    var totals = repository.metrics(table, total);
    var rows =
        repository.rows(
            table,
            query.getSort(),
            query.getDirection(),
            query.getSize(),
            (long) query.getPage() * query.getSize());
    audit.record(
        "REPORT_READ",
        "REPORT",
        type.name(),
        "Consultó reporte " + window.from() + " a " + window.to());
    return new Page(
        rows,
        query.getPage(),
        query.getSize(),
        total,
        (int) Math.min(Integer.MAX_VALUE, (total + query.getSize() - 1) / query.getSize()),
        totals,
        window.from(),
        window.to(),
        windows.now());
  }
}
