package com.odontocare.reports.service;

import com.odontocare.reports.dto.ReportContracts.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.springframework.stereotype.Component;

@Component
public class ReportCsvRenderer {
  public byte[] render(Definition definition, List<Row> rows) {
    var csv = new StringBuilder("\uFEFF");
    csv.append(
            definition.columns().stream()
                .map(c -> cell(c.label(), false))
                .reduce((a, b) -> a + "," + b)
                .orElse(""))
        .append("\r\n");
    for (var row : rows) {
      csv.append(
              definition.columns().stream()
                  .map(
                      c ->
                          cell(
                              row.values().getOrDefault(c.key(), ""),
                              Set.of("MONEY", "COUNT").contains(c.kind())))
                  .reduce((a, b) -> a + "," + b)
                  .orElse(""))
          .append("\r\n");
    }
    return csv.toString().getBytes(StandardCharsets.UTF_8);
  }

  private String cell(String value, boolean number) {
    String text = value;
    String leading = text.stripLeading();
    if (!number
        && ((!leading.isEmpty() && "=+-@".indexOf(leading.charAt(0)) >= 0)
            || text.startsWith("\t")
            || text.startsWith("\r"))) text = "'" + text;
    return "\"" + text.replace("\"", "\"\"") + "\"";
  }
}
