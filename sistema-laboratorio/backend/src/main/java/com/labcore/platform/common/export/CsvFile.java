package com.labcore.platform.common.export;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

public final class CsvFile {
  private final StringBuilder content = new StringBuilder("\uFEFF");

  public CsvFile row(Object... values) {
    content
        .append(String.join(",", Arrays.stream(values).map(this::escape).toList()))
        .append("\r\n");
    return this;
  }

  public byte[] bytes() {
    return content.toString().getBytes(StandardCharsets.UTF_8);
  }

  private String escape(Object value) {
    String text = value == null ? "" : String.valueOf(value);
    return '"' + text.replace("\"", "\"\"") + '"';
  }
}
