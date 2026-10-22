package com.odontocare.shared.pdf;

import java.util.*;

/** Presentation data; monetary calculations remain in the financial/report services. */
public final class PdfContent {
  public record Field(String label, String value) {}

  public record Column(String label, float weight, boolean numeric) {}

  public sealed interface Block permits Text, Table {}

  public record Text(String title, String value) implements Block {}

  public record Table(String title, List<Column> columns, List<List<String>> rows)
      implements Block {}

  private final List<Field> fields = new ArrayList<>(), totals = new ArrayList<>();
  private final List<Block> blocks = new ArrayList<>();

  public PdfContent field(String label, Object value) {
    fields.add(new Field(label, Objects.toString(value, "")));
    return this;
  }

  public PdfContent total(String label, Object value) {
    totals.add(new Field(label, Objects.toString(value, "")));
    return this;
  }

  public PdfContent text(String title, String value) {
    if (value != null && !value.isBlank()) blocks.add(new Text(title, value));
    return this;
  }

  public PdfContent table(String title, List<Column> columns, List<List<String>> rows) {
    blocks.add(new Table(title, List.copyOf(columns), List.copyOf(rows)));
    return this;
  }

  public List<Field> fields() {
    return List.copyOf(fields);
  }

  public List<Field> totals() {
    return List.copyOf(totals);
  }

  public List<Block> blocks() {
    return List.copyOf(blocks);
  }

  public static Column column(String label, float weight) {
    return new Column(label, weight, false);
  }

  public static Column amount(String label, float weight) {
    return new Column(label, weight, true);
  }
}
