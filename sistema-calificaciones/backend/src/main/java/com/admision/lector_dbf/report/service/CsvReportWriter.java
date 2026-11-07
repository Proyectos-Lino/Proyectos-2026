package com.admision.lector_dbf.report.service;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.math.BigDecimal;

/** Escritura incremental y protección de celdas de texto al abrir el CSV en una hoja de cálculo. */
public final class CsvReportWriter {
    private final Writer writer;
    public CsvReportWriter(Writer writer) { this.writer=writer; }

    public void row(Object... values) {
        try {
            for (int i=0;i<values.length;i++) {
                if (i>0) writer.write(';');
                Object value=values[i];
                String text=value==null ? "" : value instanceof BigDecimal decimal ? decimal.toPlainString() : value.toString();
                if (value instanceof String) {
                    text=text.replaceAll("[\\p{Cntrl}]"," ");
                    if (text.stripLeading().matches("^[=+@-].*")) text="'"+text;
                }
                writer.write('"'); writer.write(text.replace("\"","\"\"")); writer.write('"');
            }
            writer.write("\r\n");
        } catch (IOException exception) { throw new UncheckedIOException(exception); }
    }
}
