package com.odontocare.reports.service;

import com.odontocare.audit.service.AuditService;
import com.odontocare.clinical.service.ClinicalAccess;
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
public class ReportExportService {
  public enum Format {
    CSV,
    PDF
  }

  private final ReportRepository repository;
  private final ReportAccess access;
  private final ReportService reports;
  private final ReportWindowService windows;
  private final ReportDefinitions definitions;
  private final ReportCsvRenderer csv;
  private final ReportPdfRenderer pdf;
  private final InstallationProfileRepository profiles;
  private final com.odontocare.installation.repository.InstallationLogoRepository logos;
  private final ClinicalAccess clinicalAccess;
  private final AuditService audit;

  public ReportExportService(
      ReportRepository repository,
      ReportAccess access,
      ReportService reports,
      ReportWindowService windows,
      ReportDefinitions definitions,
      ReportCsvRenderer csv,
      ReportPdfRenderer pdf,
      InstallationProfileRepository profiles,
      ClinicalAccess clinicalAccess,
      AuditService audit,
      com.odontocare.installation.repository.InstallationLogoRepository logos) {
    this.repository = repository;
    this.access = access;
    this.reports = reports;
    this.windows = windows;
    this.definitions = definitions;
    this.csv = csv;
    this.pdf = pdf;
    this.profiles = profiles;
    this.clinicalAccess = clinicalAccess;
    this.audit = audit;
    this.logos = logos;
  }

  @Transactional(isolation = Isolation.REPEATABLE_READ)
  public Export export(ReportType type, ReportQuery query, Format format) {
    access.require(type, true);
    reports.validate(type, query);
    var window = windows.resolve(query);
    var table = repository.table(type, query, window);
    long count = repository.count(table);
    int limit = format == Format.CSV ? 10000 : 500;
    if (count > limit)
      throw ApiException.badRequest(
          "El resultado es demasiado amplio para esta exportación. Reduce el período o añade"
              + " filtros.");
    var rows = new ArrayList<Row>();
    for (long offset = 0; offset < count; offset += 100)
      rows.addAll(repository.rows(table, query.getSort(), query.getDirection(), 100, offset));
    var definition = definitions.get(type);
    byte[] bytes;
    if (format == Format.CSV) bytes = csv.render(definition, rows);
    else {
      var filters = new ArrayList<String>();
      if (!query.getSearch().isBlank()) filters.add("Búsqueda: " + query.getSearch());
      if (query.getStatus() != null)
        filters.add("Estado: " + definition.statuses().get(query.getStatus()));
      if (query.getCurrency() != null) filters.add("Moneda: " + query.getCurrency());
      if (query.getMethod() != null)
        filters.add(
            "Medio de pago: "
                + Map.of(
                        "CASH",
                        "Efectivo",
                        "TRANSFER",
                        "Transferencia",
                        "CARD",
                        "Tarjeta",
                        "OTHER",
                        "Otro")
                    .get(query.getMethod()));
      if (query.getDentistId() != null)
        filters.add("Profesional: " + repository.dentistName(query.getDentistId()));
      if (query.getPatientId() != null)
        filters.add("Paciente: " + repository.patientName(query.getPatientId()));
      if (query.isIncludeEarlier())
        filters.add("Vencimientos: incluye cuotas anteriores al período.");
      bytes =
          pdf.render(
              profiles.findById((short) 1).orElseThrow(),
              logos.findById((short) 1).map(logo -> logo.getContent()).orElse(null),
              definition,
              window,
              repository.metrics(table, count),
              rows,
              filters,
              clinicalAccess.actor().getDisplayName());
    }
    audit.record(
        "REPORT_EXPORTED",
        "REPORT",
        type.name(),
        "Exportó " + format + "; " + count + " registros; " + window.from() + " a " + window.to());
    return new Export(
        "reporte-"
            + type.name().toLowerCase(Locale.ROOT)
            + "-"
            + window.from()
            + "-"
            + window.to()
            + "."
            + format.name().toLowerCase(Locale.ROOT),
        format == Format.CSV ? "text/csv;charset=UTF-8" : "application/pdf",
        bytes);
  }
}
