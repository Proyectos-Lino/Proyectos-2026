package com.odontocare.shared.pdf;

import com.odontocare.installation.model.InstallationProfile;
import com.odontocare.shared.pdf.PdfContent.*;
import java.io.*;
import java.util.*;
import org.apache.pdfbox.pdmodel.*;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.springframework.stereotype.Component;

@Component
public class InstitutionalPdfRenderer {
  public byte[] render(
      InstallationProfile profile,
      byte[] logo,
      String title,
      String code,
      PdfContent content,
      boolean landscape) {
    try (var document = new PDDocument();
        var fontData = getClass().getResourceAsStream("/fonts/NotoSans-Regular.ttf");
        var output = new ByteArrayOutputStream()) {
      var font = PDType0Font.load(document, fontData);
      document.getDocumentInformation().setTitle(title + " - " + profile.getDisplayName());
      document.getDocumentInformation().setCreator("OdontoCare");
      try (var layout = new Layout(document, font, profile, logo, title, code, landscape)) {
        layout.fields(content.fields());
        layout.totals(content.totals());
        for (var block : content.blocks()) {
          if (block instanceof Text text) {
            if (!text.title().isBlank())
              layout.heading(text.title(), layout.paragraphHeight(text.value(), 9.5f));
            layout.paragraph(text.value(), 9.5f);
            layout.gap(12);
          } else if (block instanceof Table table) layout.table(table);
        }
        if (!profile.getDocumentFooter().isBlank() && !layout.shortFooter()) {
          layout.heading(
              "Nota del consultorio", layout.paragraphHeight(profile.getDocumentFooter(), 9));
          layout.paragraph(profile.getDocumentFooter(), 9);
        }
        layout.footers();
      }
      document.save(output);
      return output.toByteArray();
    } catch (IOException e) {
      throw new IllegalStateException("No se pudo generar el documento PDF.", e);
    }
  }

  private static final class Layout implements AutoCloseable {
    private static final float MARGIN = 42, BOTTOM = 62, LEADING = 13, PAD = 7;
    private static final float[] BRAND = {0.10f, 0.29f, 0.25f},
        INK = {0.14f, 0.20f, 0.23f},
        MUTED = {0.37f, 0.43f, 0.46f},
        LINE = {0.82f, 0.87f, 0.87f},
        PALE = {0.94f, 0.97f, 0.96f};
    private final PDDocument document;
    private final PDType0Font font;
    private final InstallationProfile identity;
    private final String title, code;
    private final float width, height, usable;
    private final PDImageXObject logo;
    private PDPageContentStream stream;
    private float y;

    Layout(
        PDDocument document,
        PDType0Font font,
        InstallationProfile identity,
        byte[] logo,
        String title,
        String code,
        boolean landscape)
        throws IOException {
      this.document = document;
      this.font = font;
      this.identity = identity;
      this.title = title;
      this.code = code;
      width = landscape ? PDRectangle.A4.getHeight() : PDRectangle.A4.getWidth();
      height = landscape ? PDRectangle.A4.getWidth() : PDRectangle.A4.getHeight();
      usable = width - 2 * MARGIN;
      this.logo =
          logo == null
              ? null
              : PDImageXObject.createFromByteArray(document, logo, "Logo del consultorio");
      newPage();
    }

    void newPage() throws IOException {
      if (stream != null) stream.close();
      var page = new PDPage(new PDRectangle(width, height));
      document.addPage(page);
      stream = new PDPageContentStream(document, page);
      fill(BRAND, 0, height - 9, width, 9);
      y = height - 43;
      boolean first = document.getNumberOfPages() == 1;
      float brandingWidth = usable - (first && logo != null ? 76 : 0);
      if (first && logo != null) {
        float scale = Math.min(58f / logo.getWidth(), 58f / logo.getHeight());
        stream.drawImage(
            logo,
            width - MARGIN - logo.getWidth() * scale,
            height - 91,
            logo.getWidth() * scale,
            logo.getHeight() * scale);
      }
      for (var line : wrap(identity.getDisplayName(), first ? 16 : 11, brandingWidth)) {
        text(line, MARGIN, y, first ? 16 : 11, BRAND);
        y -= first ? 20 : 15;
      }
      if (first) {
        headerLine(identity.getLegalName(), brandingWidth);
        headerLine(identity.getAddress(), brandingWidth);
        headerLine(
            String.join(
                "  |  ",
                java.util.stream.Stream.of(identity.getPhone(), identity.getEmail())
                    .filter(v -> !v.isBlank())
                    .toList()),
            brandingWidth);
        for (var line : wrap(identity.getDocumentHeader(), 8.5f, usable)) {
          if (!line.isBlank()) {
            text(line, MARGIN, y, 8.5f, MUTED);
            y -= 12;
          }
        }
        y -= 12;
        var titleLines = wrap(title, 19, usable - 24);
        float bandHeight = titleLines.size() * 24 + 43;
        fill(PALE, MARGIN, y - bandHeight, usable, bandHeight);
        float baseline = y - 18;
        text(
            code.isBlank() ? "REPORTE ADMINISTRATIVO" : "DOCUMENTO INTERNO  |  " + code,
            MARGIN + 12,
            baseline,
            8.5f,
            BRAND);
        baseline -= 27;
        for (var line : titleLines) {
          text(line, MARGIN + 12, baseline, 19, BRAND);
          baseline -= 24;
        }
        y -= bandHeight + 18;
      } else {
        for (var line : wrap(title + (code.isBlank() ? "" : "  |  " + code), 9, usable)) {
          text(line, MARGIN, y, 9, MUTED);
          y -= 13;
        }
        y -= 6;
        rule(y);
        y -= 15;
      }
    }

    void headerLine(String value, float available) throws IOException {
      if (value.isBlank()) return;
      for (var line : wrap(value, 8.5f, available)) {
        text(line, MARGIN, y, 8.5f, MUTED);
        y -= 12;
      }
    }

    void ensure(float size) throws IOException {
      if (y - size < BOTTOM) newPage();
    }

    void gap(float size) {
      y -= size;
    }

    void heading(String value, float following) throws IOException {
      ensure(30 + following);
      text(safe(value), MARGIN, y, 11, BRAND);
      y -= 8;
      rule(y);
      y -= 17;
    }

    void paragraph(String value, float size) throws IOException {
      for (var line : wrap(value, size, usable)) {
        ensure(size + 6);
        text(line, MARGIN, y, size, INK);
        y -= size + 5;
      }
    }

    float paragraphHeight(String value, float size) throws IOException {
      return Math.min(48, wrap(value, size, usable).size() * (size + 5));
    }

    void fields(List<Field> fields) throws IOException {
      if (fields.isEmpty()) return;
      heading("Información del documento", 25);
      float cellWidth = usable / 2, labelWidth = width > 700 ? 95 : 86;
      for (int index = 0; index < fields.size(); index += 2) {
        List<List<String>> labels = new ArrayList<>(), values = new ArrayList<>();
        int count = 1;
        for (int offset = 0; offset < 2 && index + offset < fields.size(); offset++) {
          var field = fields.get(index + offset);
          var key = wrap(field.label() + ":", 8.5f, labelWidth - 7);
          var value =
              wrap(field.value().isBlank() ? "-" : field.value(), 9, cellWidth - labelWidth - 12);
          labels.add(key);
          values.add(value);
          count = Math.max(count, Math.max(key.size(), value.size()));
        }
        ensure(Math.min(count * LEADING + 5, height - 170));
        for (int line = 0; line < count; line++) {
          ensure(LEADING);
          for (int column = 0; column < labels.size(); column++) {
            float x = MARGIN + column * cellWidth;
            if (line < labels.get(column).size())
              text(labels.get(column).get(line), x, y, 8.5f, MUTED);
            if (line < values.get(column).size())
              text(values.get(column).get(line), x + labelWidth, y, 9, INK);
          }
          y -= LEADING;
        }
        gap(5);
      }
      gap(11);
    }

    void totals(List<Field> totals) throws IOException {
      if (totals.isEmpty()) return;
      heading("Resumen", 55);
      int columns = Math.min(3, totals.size());
      float cellWidth = usable / columns;
      for (int index = 0; index < totals.size(); index += columns) {
        float rowHeight = 59;
        for (int offset = 0; offset < columns && index + offset < totals.size(); offset++)
          rowHeight =
              Math.max(
                  rowHeight,
                  32 + wrap(totals.get(index + offset).label(), 8.5f, cellWidth - 24).size() * 12);
        ensure(rowHeight + 8);
        for (int offset = 0; offset < columns && index + offset < totals.size(); offset++) {
          var field = totals.get(index + offset);
          float x = MARGIN + offset * cellWidth;
          fill(PALE, x, y - rowHeight, cellWidth - 7, rowHeight);
          float baseline = y - 17;
          for (var line : wrap(field.label(), 8.5f, cellWidth - 24)) {
            text(line, x + 10, baseline, 8.5f, MUTED);
            baseline -= 12;
          }
          float valueSize =
              Math.min(13, (cellWidth - 24) / Math.max(1, measure(field.value(), 13)) * 13);
          text(safe(field.value()), x + 10, baseline - 7, valueSize, BRAND);
        }
        y -= rowHeight + 7;
      }
      gap(12);
    }

    void table(Table table) throws IOException {
      var columns = table.columns();
      if (columns.isEmpty()) return;
      float totalWeight = columns.stream().map(Column::weight).reduce(0f, Float::sum);
      float[] widths = new float[columns.size()];
      for (int i = 0; i < widths.length; i++)
        widths[i] = usable * columns.get(i).weight() / totalWeight;
      float numericWidth = 0, otherWidth = 0;
      float[] desired = widths.clone();
      boolean[] fixed = new boolean[widths.length];
      int otherColumns = 0;
      for (int i = 0; i < columns.size(); i++) {
        fixed[i] = columns.get(i).numeric() || columns.get(i).label().equals("Medio");
        for (var row : table.rows())
          if (i < row.size()
              && row.get(i)
                  .matches(
                      "(?:\\d{2}/\\d{2}/\\d{4}|\\d{4}-\\d{2}-\\d{2})(?:"
                          + " \\d{2}:\\d{2})?|[A-Z0-9-]+-\\d{6}"))
            fixed[i] = true;
        if (fixed[i]) {
          for (var row : table.rows())
            if (i < row.size()) desired[i] = Math.max(desired[i], measure(row.get(i), 9) + 2 * PAD + 1);
          numericWidth += desired[i];
        } else {
          otherWidth += widths[i];
          otherColumns++;
        }
      }
      if (otherWidth > 0 && numericWidth + otherColumns * 28 < usable) {
        for (int i = 0; i < widths.length; i++)
          widths[i] = fixed[i] ? desired[i] : widths[i] * (usable - numericWidth) / otherWidth;
      }
      List<List<String>> headers = new ArrayList<>();
      int headerLines = 1;
      for (int i = 0; i < widths.length; i++) {
        var lines = wrap(columns.get(i).label(), 8.5f, widths[i] - 2 * PAD);
        headers.add(lines);
        headerLines = Math.max(headerLines, lines.size());
      }
      float headerHeight = headerLines * 12 + 2 * PAD;
      heading(table.title(), headerHeight + 34);
      tableHeader(headers, widths, headerHeight);
      if (table.rows().isEmpty()) {
        ensure(30);
        text("Sin registros.", MARGIN + PAD, y - 18, 9, MUTED);
        y -= 32;
        gap(14);
        return;
      }
      int rowIndex = 0;
      for (var row : table.rows()) {
        List<List<String>> cells = new ArrayList<>();
        int lines = 1;
        for (int i = 0; i < widths.length; i++) {
          var wrapped = wrap(i < row.size() ? row.get(i) : "", 9, widths[i] - 2 * PAD);
          cells.add(wrapped);
          lines = Math.max(lines, wrapped.size());
        }
        int offset = 0;
        while (offset < lines) {
          float rowHeight = (lines - offset) * LEADING + 2 * PAD;
          // Keep ordinary rows together; exceptionally long cells continue without clipping.
          if (y - rowHeight < BOTTOM && offset == 0 && rowHeight < height - 170) {
            newPage();
            heading(table.title() + " (continuación)", headerHeight + 25);
            tableHeader(headers, widths, headerHeight);
          }
          int fit = (int) Math.floor((y - BOTTOM - 2 * PAD) / LEADING);
          if (fit < 1) {
            newPage();
            heading(table.title() + " (continuación)", headerHeight + 25);
            tableHeader(headers, widths, headerHeight);
            fit = (int) Math.floor((y - BOTTOM - 2 * PAD) / LEADING);
          }
          int count = Math.min(lines - offset, fit);
          float h = count * LEADING + 2 * PAD;
          if (rowIndex % 2 == 0)
            fill(new float[] {0.965f, 0.976f, 0.974f}, MARGIN, y - h, usable, h);
          float x = MARGIN;
          for (int i = 0; i < widths.length; i++) {
            for (int j = 0; j < count; j++) {
              int at = offset + j;
              if (at < cells.get(i).size()) {
                var value = cells.get(i).get(at);
                float tx =
                    columns.get(i).numeric() ? x + widths[i] - PAD - measure(value, 9) : x + PAD;
                text(value, tx, y - PAD - 9 - j * LEADING, 9, INK);
              }
            }
            x += widths[i];
          }
          rule(y - h);
          y -= h;
          offset += count;
          if (offset < lines) {
            newPage();
            heading(table.title() + " (continuación)", headerHeight + 25);
            tableHeader(headers, widths, headerHeight);
          }
        }
        rowIndex++;
      }
      gap(19);
    }

    void tableHeader(List<List<String>> headers, float[] widths, float h) throws IOException {
      fill(BRAND, MARGIN, y - h, usable, h);
      float x = MARGIN;
      for (int i = 0; i < widths.length; i++) {
        int line = 0;
        for (var value : headers.get(i)) {
          text(value, x + PAD, y - PAD - 9 - line * 12, 8.5f, new float[] {1, 1, 1});
          line++;
        }
        x += widths[i];
      }
      y -= h;
    }

    void footers() throws IOException {
      stream.close();
      stream = null;
      int total = document.getNumberOfPages();
      int number = 0;
      for (var page : document.getPages()) {
        try (var footer =
            new PDPageContentStream(
                document, page, PDPageContentStream.AppendMode.APPEND, true, true)) {
          footer.setStrokingColor(LINE[0], LINE[1], LINE[2]);
          footer.moveTo(MARGIN, 46);
          footer.lineTo(width - MARGIN, 46);
          footer.stroke();
          if (number + 1 == total && shortFooter())
            drawText(footer, identity.getDocumentFooter(), MARGIN, 58, 8, MUTED);
          drawText(footer, "OdontoCare | Documento de gestión interna", MARGIN, 31, 8, MUTED);
          var value = "Página " + (++number) + " de " + total;
          drawText(footer, value, width - MARGIN - measure(value, 8), 31, 8, MUTED);
        }
      }
    }

    boolean shortFooter() throws IOException {
      return !identity.getDocumentFooter().isBlank()
          && wrap(identity.getDocumentFooter(), 8, usable).size() == 1;
    }

    void rule(float baseline) throws IOException {
      stream.setStrokingColor(LINE[0], LINE[1], LINE[2]);
      stream.setLineWidth(0.5f);
      stream.moveTo(MARGIN, baseline);
      stream.lineTo(width - MARGIN, baseline);
      stream.stroke();
    }

    void fill(float[] color, float x, float y, float w, float h) throws IOException {
      stream.setNonStrokingColor(color[0], color[1], color[2]);
      stream.addRect(x, y, w, h);
      stream.fill();
    }

    void text(String value, float x, float y, float size, float[] color) throws IOException {
      drawText(stream, value, x, y, size, color);
    }

    void drawText(
        PDPageContentStream out, String value, float x, float y, float size, float[] color)
        throws IOException {
      out.setNonStrokingColor(color[0], color[1], color[2]);
      out.beginText();
      out.setFont(font, size);
      out.newLineAtOffset(x, y);
      out.showText(safe(value));
      out.endText();
    }

    float measure(String value, float size) throws IOException {
      return font.getStringWidth(safe(value)) / 1000 * size;
    }

    String safe(String value) {
      var result = new StringBuilder();
      Objects.toString(value, "")
          .codePoints()
          .forEach(
              cp -> {
                if (Character.isISOControl(cp)) {
                  result.append(' ');
                  return;
                }
                try {
                  font.encode(new String(Character.toChars(cp)));
                  result.appendCodePoint(cp);
                } catch (IOException | IllegalArgumentException e) {
                  result.append('?');
                }
              });
      return result.toString();
    }

    List<String> wrap(String value, float size, float available) throws IOException {
      var rows = new ArrayList<String>();
      var line = new StringBuilder();
      for (int cp : safe(value).codePoints().toArray()) {
        String next = new String(Character.toChars(cp));
        if (measure(line + next, size) > available && !line.isEmpty()) {
          int space = line.lastIndexOf(" ");
          if (space > 0) {
            rows.add(line.substring(0, space));
            line.delete(0, space + 1);
          } else {
            rows.add(line.toString());
            line.setLength(0);
          }
        }
        line.append(next);
      }
      rows.add(line.toString().strip());
      return rows;
    }

    @Override
    public void close() throws IOException {
      if (stream != null) stream.close();
    }
  }
}
