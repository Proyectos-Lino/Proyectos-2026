package com.odontocare.reports.service;

import com.odontocare.installation.model.InstallationProfile;
import com.odontocare.reports.dto.ReportContracts.*;
import com.odontocare.shared.pdf.*;
import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.format.DateTimeFormatter;
import java.util.*;
import org.springframework.stereotype.Component;

@Component
public class ReportPdfRenderer {
  private final InstitutionalPdfRenderer renderer;

  public ReportPdfRenderer(InstitutionalPdfRenderer renderer) {
    this.renderer = renderer;
  }

  public byte[] render(
      InstallationProfile profile,
      byte[] logo,
      Definition definition,
      Window window,
      List<Metric> totals,
      List<Row> rows,
      List<String> filters,
      String actor) {
    var content =
        new PdfContent()
            .field(
                "Período",
                date(window.from().toString(), profile.getDateFormat())
                    + " a "
                    + date(window.to().toString(), profile.getDateFormat()))
            .field("Preparado por", actor)
            .field("Registros", rows.size())
            .field("Referencia horaria", "Fechas y horas del consultorio");
    for (var filter : filters) {
      int separator = filter.indexOf(':');
      content.field(
          separator > 0 ? filter.substring(0, separator) : "Filtro",
          separator > 0 ? filter.substring(separator + 1).strip() : filter);
    }
    for (var metric : totals)
      content.total(
          metric.label(),
          metric.currency() == null ? metric.value() : money(metric.value(), metric.currency()));
    content.text("Criterio del reporte", definition.description());
    var columns = new ArrayList<PdfContent.Column>();
    columns.add(PdfContent.column("N.º", 0.5f));
    for (var column : definition.columns()) {
      boolean numeric = Set.of("MONEY", "COUNT").contains(column.kind());
      float weight =
          numeric
              ? 1.3f
              : column.kind().equals("DATE_TIME")
                  ? 1.7f
                  : column.kind().equals("DATE")
                      ? 1.2f
                      : Set.of("patient", "dentist", "description", "service")
                              .contains(column.key())
                          ? 2.1f
                          : 1.4f;
      columns.add(new PdfContent.Column(column.label(), weight, numeric));
    }
    var values = new ArrayList<List<String>>();
    int index = 0;
    for (var row : rows) {
      var cells = new ArrayList<String>();
      cells.add(String.valueOf(++index));
      for (var column : definition.columns()) {
        String value = row.values().getOrDefault(column.key(), "");
        if (column.kind().equals("MONEY") && !value.isBlank())
          value = money(value, row.values().getOrDefault("currency", profile.getCurrency()));
        else if (column.kind().equals("DATE_TIME") && !value.isBlank())
          value =
              date(value.substring(0, 10), profile.getDateFormat()) + " " + value.substring(11, 16);
        else if (column.kind().equals("DATE") && !value.isBlank())
          value = date(value, profile.getDateFormat());
        cells.add(value.isBlank() ? "-" : value);
      }
      values.add(cells);
    }
    if (columns.size() > 10) {
      int split = 7;
      content.table(
          "Detalle del resultado filtrado",
          columns.subList(0, split),
          values.stream().map(v -> List.copyOf(v.subList(0, split))).toList());
      var detailColumns = new ArrayList<PdfContent.Column>();
      detailColumns.add(columns.get(0));
      detailColumns.addAll(columns.subList(split, columns.size()));
      var detailRows =
          values.stream()
              .map(
                  v -> {
                    var detail = new ArrayList<String>();
                    detail.add(v.get(0));
                    detail.addAll(v.subList(split, v.size()));
                    return List.copyOf(detail);
                  })
              .toList();
      content.table("Importes y avance por número de registro", detailColumns, detailRows);
    } else content.table("Detalle del resultado filtrado", columns, values);
    return renderer.render(
        profile, logo, definition.title(), "", content, definition.columns().size() >= 6);
  }

  private String money(String value, String currency) {
    var format = NumberFormat.getNumberInstance(Locale.US);
    format.setMinimumFractionDigits(2);
    format.setMaximumFractionDigits(2);
    return currency + " " + format.format(new BigDecimal(value));
  }

  private String date(String value, String format) {
    return java.time.LocalDate.parse(value)
        .format(
            DateTimeFormatter.ofPattern(
                format.equals("MDY")
                    ? "MM/dd/yyyy"
                    : format.equals("YMD") ? "yyyy-MM-dd" : "dd/MM/yyyy"));
  }
}
