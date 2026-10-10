package com.labcore.platform.catalog;

import java.util.List;

final class CatalogCsvExporter {

  private CatalogCsvExporter() {}

  static String render(List<String> headers, List<List<String>> rows) {
    StringBuilder csv = new StringBuilder(String.join(",", headers)).append('\n');
    for (List<String> row : rows) {
      csv.append(
          row.stream()
              .map(CatalogCsvExporter::escape)
              .collect(java.util.stream.Collectors.joining(",")));
      csv.append('\n');
    }
    return csv.toString();
  }

  private static String escape(String value) {
    String text = value == null ? "" : value;
    return '"' + text.replace("\"", "\"\"") + '"';
  }
}
