package com.odontocare.reports.dto;

import com.odontocare.reports.model.ReportType;
import java.time.*;
import java.util.*;

public final class ReportContracts {
  private ReportContracts() {}

  public record Column(String key, String label, String kind) {}

  public record Definition(
      ReportType type,
      String title,
      String description,
      List<Column> columns,
      String defaultSort,
      boolean dentist,
      boolean patient,
      boolean currency,
      boolean method,
      Map<String, String> statuses) {}

  public record Catalog(List<Definition> reports, List<String> currencies, boolean canExport) {}

  public record Row(
      String id,
      UUID patientId,
      UUID dentistId,
      UUID entityId,
      UUID documentId,
      String state,
      Map<String, String> values) {}

  public record Metric(String key, String label, String kind, String value, String currency) {}

  public record Page(
      List<Row> items,
      int page,
      int size,
      long totalElements,
      int totalPages,
      List<Metric> totals,
      LocalDate from,
      LocalDate to,
      Instant generatedAt) {}

  public record Window(LocalDate from, LocalDate to, ZoneId zone) {
    public Instant start() {
      return from.atStartOfDay(zone).toInstant();
    }

    public Instant end() {
      return to.plusDays(1).atStartOfDay(zone).toInstant();
    }
  }

  public record Export(String name, String mediaType, byte[] bytes) {}
}
