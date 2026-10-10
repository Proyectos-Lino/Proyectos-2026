package com.labcore.platform.reports;

import com.labcore.platform.catalog.ResultFieldKind;
import com.labcore.platform.organization.OrganizationDtos;
import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.Image;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.BaseFont;
import com.lowagie.text.pdf.ColumnText;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfPageEventHelper;
import com.lowagie.text.pdf.PdfWriter;
import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.Comparator;
import java.util.Objects;
import org.springframework.stereotype.Component;

@Component
public class LaboratoryReportPdfGenerator {
  private static final Color PRIMARY = new Color(20, 20, 20);
  private static final Color ACCENT = new Color(13, 148, 136);
  private static final Color DARK = new Color(20, 32, 51);
  private static final Color MUTED = new Color(83, 99, 120);
  private static final Color BORDER = new Color(210, 219, 229);
  private static final Color SURFACE = new Color(246, 248, 251);
  private static final Color ACCENT_SURFACE = new Color(239, 250, 248);
  private static final Color SECTION = new Color(226, 226, 226);
  private static final String DEFAULT_TIME_ZONE = "America/Lima";

  public byte[] generate(
      LaboratoryReportDtos.Snapshot snapshot, LaboratoryReportStatus status, String checksum) {
    try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
      Document document = new Document(PageSize.LETTER, 40, 40, 40, 38);
      PdfWriter writer = PdfWriter.getInstance(document, output);
      writer.setPageEvent(
          new ReportPageEvent(snapshot.reportCode(), checksum, organizationName(snapshot)));
      document.addTitle("Informe de resultados " + snapshot.reportCode());
      document.addSubject("Resultados validados de laboratorio");
      document.addCreator(organizationName(snapshot));
      document.open();

      addHeader(document, snapshot, status);
      addRecipient(document, snapshot);
      addSamples(document, snapshot);
      addClosing(document, snapshot, checksum);
      document.close();
      return output.toByteArray();
    } catch (DocumentException | java.io.IOException exception) {
      throw new IllegalStateException("No fue posible generar el informe PDF.", exception);
    }
  }

  private void addHeader(
      Document document, LaboratoryReportDtos.Snapshot snapshot, LaboratoryReportStatus status)
      throws DocumentException {
    PdfPTable header = new PdfPTable(new float[] {1.2f, 3.8f});
    header.setWidthPercentage(100);
    PdfPCell logo = cell(null, Rectangle.BOX, Color.WHITE, 8);
    logo.setBorderColor(DARK);
    logo.setHorizontalAlignment(Element.ALIGN_CENTER);
    addLogo(logo, snapshot.organization());
    if (snapshot.organization() == null || snapshot.organization().logoDataUrl() == null) {
      logo.addElement(paragraph(organizationName(snapshot), 10, Font.BOLD, PRIMARY));
    }
    header.addCell(logo);

    PdfPCell identity = cell(null, Rectangle.BOX, Color.WHITE, 7);
    identity.setBorderColor(DARK);
    identity.setHorizontalAlignment(Element.ALIGN_CENTER);
    Paragraph legalName =
        paragraph(
            snapshot.organization() == null
                ? organizationName(snapshot)
                : safe(snapshot.organization().legalName()),
            11,
            Font.BOLD,
            PRIMARY);
    legalName.setAlignment(Element.ALIGN_CENTER);
    identity.addElement(legalName);
    Paragraph address = paragraph(institutionalContact(snapshot), 7.2f, Font.NORMAL, DARK);
    address.setAlignment(Element.ALIGN_CENTER);
    identity.addElement(address);
    header.addCell(identity);
    document.add(header);

    document.add(sectionBand(snapshot.laboratoryAreaName().toUpperCase()));
    document.add(
        titleBand(
            "INFORME DE ENSAYO N° "
                + snapshot.reportCode()
                + " · VERSIÓN "
                + snapshot.version()
                + " · "
                + statusLabel(status)));
  }

  private void addRecipient(Document document, LaboratoryReportDtos.Snapshot snapshot)
      throws DocumentException {
    document.add(sectionBand("DATOS GENERALES"));
    PdfPTable table = new PdfPTable(new float[] {1, 1});
    table.setWidthPercentage(100);
    table.addCell(infoCell("Nombre del solicitante", snapshot.clientName()));
    table.addCell(infoCell("Documento", snapshot.clientDocument()));
    table.addCell(infoCell("Nombre consignado en informe", snapshot.reportName()));
    table.addCell(infoCell("Correo", snapshot.reportEmail()));
    PdfPCell address = infoCell("Dirección", snapshot.reportAddress());
    address.setColspan(2);
    table.addCell(address);
    table.addCell(infoCell("Orden de servicio", snapshot.serviceOrderCode()));
    table.addCell(infoCell("Muestras informadas", String.valueOf(snapshot.samples().size())));
    document.add(table);
  }

  private void addSamples(Document document, LaboratoryReportDtos.Snapshot snapshot)
      throws DocumentException {
    for (LaboratoryReportDtos.SampleResult sample : snapshot.samples()) {
      document.add(sectionBand("DATOS DE LA MUESTRA " + sample.sequence()));
      document.add(sampleHeading(sample));

      PdfPTable sampleInfo = new PdfPTable(new float[] {1, 1, 1});
      sampleInfo.setWidthPercentage(100);
      sampleInfo.addCell(infoCell("Identificación del cliente", sample.clientIdentification()));
      sampleInfo.addCell(infoCell("Productor", sample.producerName()));
      sampleInfo.addCell(infoCell("Documento del productor", sample.producerDocument()));
      sampleInfo.addCell(infoCell("Origen", sample.origin()));
      sampleInfo.addCell(infoCell("Lugar de muestreo", sample.samplingLocation()));
      sampleInfo.addCell(infoCell("Descripción física", sample.physicalDescription()));
      sampleInfo.addCell(
          infoCell(
              "Cantidad",
              sample.quantity() == null
                  ? null
                  : decimal(sample.quantity())
                      + (sample.quantityUnit() == null ? "" : " " + sample.quantityUnit())));
      sampleInfo.addCell(infoCell("Envase", sample.containerDescription()));
      sampleInfo.addCell(infoCell("Lote", sample.batchNumber()));
      sampleInfo.addCell(infoCell("Marca", sample.brand()));
      sampleInfo.addCell(
          infoCell(
              "Fecha de muestreo",
              sample.sampledAt() == null ? null : formatDate(snapshot, sample.sampledAt())));
      sampleInfo.setSpacingAfter(7);
      document.add(sampleInfo);

      for (LaboratoryReportDtos.TestResult test : sample.tests()) {
        addTest(document, test, snapshot);
      }
      if (sample.observations() != null) {
        PdfPTable observations = new PdfPTable(1);
        observations.setWidthPercentage(100);
        observations.addCell(noteCell("Observaciones de la muestra", sample.observations()));
        observations.setSpacingAfter(7);
        document.add(observations);
      }
    }
  }

  private PdfPTable sampleHeading(LaboratoryReportDtos.SampleResult sample) {
    PdfPTable heading = new PdfPTable(new float[] {3, 1.25f});
    heading.setWidthPercentage(100);
    heading.setSpacingBefore(2);

    PdfPCell description = cell(null, Rectangle.BOX, Color.WHITE, 7);
    description.setBorderColor(BORDER);
    description.addElement(paragraph("NOMBRE DE LA MUESTRA", 6.5f, Font.BOLD, DARK));
    description.addElement(paragraph(productDescription(sample), 9, Font.NORMAL, PRIMARY));
    description.addElement(
        paragraph("Tipo técnico: " + safe(sample.sampleTypeName()), 8, Font.NORMAL, MUTED));
    heading.addCell(description);

    PdfPCell code = cell(null, Rectangle.BOX, Color.WHITE, 7);
    code.setBorderColor(BORDER);
    code.setHorizontalAlignment(Element.ALIGN_CENTER);
    code.addElement(paragraph("CÓDIGO DE LA MUESTRA", 6.5f, Font.BOLD, DARK));
    code.addElement(paragraph(safe(sample.laboratoryCode()), 9, Font.NORMAL, PRIMARY));
    heading.addCell(code);
    return heading;
  }

  private String productDescription(LaboratoryReportDtos.SampleResult sample) {
    return sample.varietyName() == null
        ? safe(sample.productName())
        : "%s - %s".formatted(safe(sample.productName()), sample.varietyName());
  }

  private void addTest(
      Document document,
      LaboratoryReportDtos.TestResult test,
      LaboratoryReportDtos.Snapshot snapshot)
      throws DocumentException {
    if (!test.values().isEmpty()) {
      PdfPTable values = new PdfPTable(new float[] {2.1f, 1.25f, 1.05f, 2.05f});
      values.setWidthPercentage(100);
      values.setHeaderRows(1);
      values.addCell(tableHeader("Determinación"));
      values.addCell(tableHeader("Unidad de medida"));
      values.addCell(tableHeader("Resultado"));
      values.addCell(tableHeader("Método"));
      test.values().stream()
          .sorted(
              Comparator.comparing(LaboratoryReportDtos.Value::label)
                  .thenComparingInt(LaboratoryReportDtos.Value::repetition))
          .forEach(
              value -> {
                values.addCell(tableBody(valueLabel(value)));
                values.addCell(tableBody(value.unitSymbol()));
                values.addCell(tableBody(resultValue(value)));
                values.addCell(tableBody(test.methodName()));
              });
      values.setSpacingAfter(4);
      document.add(values);
      for (LaboratoryReportDtos.Value value : test.values()) {
        if (value.fieldKind() == ResultFieldKind.CALCULATED) {
          document.add(
              paragraph(
                  "Trazabilidad del cálculo - %s: %s"
                      .formatted(value.label(), safe(value.formulaUsed())),
                  7,
                  Font.NORMAL,
                  MUTED));
        }
      }
    }
    if (test.matrix() != null) {
      PdfPTable matrixHeading = new PdfPTable(new float[] {1.7f, 2.3f});
      matrixHeading.setWidthPercentage(100);
      matrixHeading.addCell(testHeadingCell("Determinación", test.testName()));
      matrixHeading.addCell(testHeadingCell("Método", test.methodName()));
      document.add(matrixHeading);
      addMatrix(document, test.matrix());
    }
    if (test.analystObservation() != null) {
      PdfPTable observation = new PdfPTable(1);
      observation.setWidthPercentage(100);
      observation.addCell(noteCell("Observación técnica", test.analystObservation()));
      observation.setSpacingAfter(8);
      document.add(observation);
    }
  }

  private void addMatrix(Document document, LaboratoryReportDtos.Matrix matrix)
      throws DocumentException {
    addMatrixMetadata(document, matrix);

    PdfPTable table = new PdfPTable(matrix.columnCount() + 1);
    table.setWidthPercentage(100);
    table.setHeaderRows(1);
    table.addCell(tableHeader("Yema"));
    matrix.columns().stream()
        .sorted(Comparator.comparingInt(LaboratoryReportDtos.MatrixColumn::index))
        .forEach(column -> table.addCell(tableHeader(safe(column.label()))));
    for (int row = 0; row < matrix.rowCount(); row++) {
      table.addCell(rowNumberCell(row + 1));
      for (int column = 0; column < matrix.columnCount(); column++) {
        int currentRow = row;
        int currentColumn = column;
        String value =
            matrix.cells().stream()
                .filter(cell -> cell.row() == currentRow && cell.column() == currentColumn)
                .findFirst()
                .map(this::matrixValue)
                .orElse("-");
        table.addCell(tableBody(value));
      }
    }
    table.setSpacingAfter(7);
    document.add(table);
    addMatrixSummary(document, matrix);
  }

  private void addMatrixSummary(Document document, LaboratoryReportDtos.Matrix matrix)
      throws DocumentException {
    if (matrix.categorySummary().isEmpty()) {
      return;
    }
    PdfPTable summary = new PdfPTable(new float[] {2.2f, 0.8f, 0.9f});
    summary.setWidthPercentage(72);
    summary.setHorizontalAlignment(Element.ALIGN_LEFT);
    summary.addCell(tableHeader("Resumen por categoría"));
    summary.addCell(tableHeader("Cantidad"));
    summary.addCell(tableHeader("Porcentaje"));
    matrix
        .categorySummary()
        .forEach(
            category -> {
              summary.addCell(tableBody(category.category()));
              summary.addCell(tableBody(String.valueOf(category.count())));
              summary.addCell(tableBody(decimal(category.percentage()) + " %"));
            });
    summary.setSpacingAfter(7);
    document.add(summary);
  }

  private void addMatrixMetadata(Document document, LaboratoryReportDtos.Matrix matrix)
      throws DocumentException {
    document.add(paragraph("Configuración de cargadores", 8, Font.BOLD, PRIMARY));
    int columns = Math.min(Math.max(matrix.columnCount(), 1), 3);
    PdfPTable metadata = new PdfPTable(columns);
    metadata.setWidthPercentage(100);
    matrix.columns().stream()
        .sorted(Comparator.comparingInt(LaboratoryReportDtos.MatrixColumn::index))
        .forEach(column -> metadata.addCell(matrixMetadataCell(column)));
    metadata.completeRow();
    metadata.setSpacingAfter(6);
    document.add(metadata);
  }

  private PdfPCell matrixMetadataCell(LaboratoryReportDtos.MatrixColumn column) {
    PdfPCell cell = cell(null, Rectangle.BOX, Color.WHITE, 7);
    cell.setBorderColor(BORDER);
    cell.addElement(paragraph(safe(column.label()), 8, Font.BOLD, PRIMARY));
    cell.addElement(
        paragraph(
            "Distancia: %s  |  Diámetro: %s"
                .formatted(decimalOrDash(column.distance()), decimalOrDash(column.diameter())),
            7,
            Font.NORMAL,
            MUTED));
    cell.addElement(paragraph("Forma: " + safe(column.shape()), 7, Font.NORMAL, MUTED));
    return cell;
  }

  private void addClosing(
      Document document, LaboratoryReportDtos.Snapshot snapshot, String checksum)
      throws DocumentException {
    if (snapshot.generalObservations() != null) {
      PdfPTable observations = new PdfPTable(1);
      observations.setWidthPercentage(100);
      observations.addCell(noteCell("Observaciones generales", snapshot.generalObservations()));
      observations.setSpacingAfter(8);
      document.add(observations);
    }

    OrganizationDtos.DocumentIdentity organization = snapshot.organization();
    PdfPTable closing = new PdfPTable(new float[] {1, 1});
    closing.setWidthPercentage(100);
    closing.setKeepTogether(true);
    closing.addCell(tableHeader("CONDICIONES DEL INFORME"));
    closing.addCell(tableHeader("FIRMA Y EMISIÓN"));

    String legalText =
        organization == null || organization.legalText() == null
            ? "Los resultados se refieren únicamente a las muestras analizadas. La reproducción parcial del informe requiere autorización del laboratorio."
            : organization.legalText();
    closing.addCell(infoCell("Condiciones", legalText));

    String signer = authorizedSigner(organization);
    String validators =
        snapshot.samples().stream()
            .flatMap(sample -> sample.tests().stream())
            .map(LaboratoryReportDtos.TestResult::validatorName)
            .filter(value -> value != null && !value.isBlank())
            .distinct()
            .collect(java.util.stream.Collectors.joining(", "));
    closing.addCell(
        infoCell(
            "Responsable autorizado",
            signer
                + "\nValidación técnica: "
                + safe(validators.isBlank() ? null : validators)
                + "\nFecha de emisión: "
                + formatDate(snapshot, snapshot.generatedAt())));
    document.add(closing);

    PdfPTable control = new PdfPTable(new float[] {1.1f, 0.55f, 1.85f});
    control.setWidthPercentage(100);
    control.addCell(tableBody("Código: " + snapshot.reportCode()));
    control.addCell(tableBody("Versión: " + snapshot.version()));
    control.addCell(tableBody("Integridad: " + checksum.substring(0, 24)));
    control.setSpacingBefore(0);
    document.add(control);
  }

  private void addLogo(PdfPCell cell, OrganizationDtos.DocumentIdentity organization) {
    if (organization == null || organization.logoDataUrl() == null) {
      return;
    }
    try {
      String encoded =
          organization.logoDataUrl().substring(organization.logoDataUrl().indexOf(',') + 1);
      Image logo = Image.getInstance(Base64.getDecoder().decode(encoded));
      logo.scaleToFit(130, 50);
      logo.setSpacingAfter(4);
      cell.addElement(logo);
    } catch (RuntimeException | java.io.IOException ignored) {
      // Un logo inválido no debe impedir la emisión del documento oficial.
    }
  }

  private String formatDate(LaboratoryReportDtos.Snapshot snapshot, java.time.Instant value) {
    String zone =
        snapshot.organization() == null || snapshot.organization().timeZone() == null
            ? DEFAULT_TIME_ZONE
            : snapshot.organization().timeZone();
    return DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm").withZone(ZoneId.of(zone)).format(value);
  }

  private String organizationName(LaboratoryReportDtos.Snapshot snapshot) {
    return snapshot.organization() == null
        ? "Gestión de laboratorio"
        : snapshot.organization().commercialName();
  }

  private String institutionalContact(LaboratoryReportDtos.Snapshot snapshot) {
    if (snapshot.organization() == null) {
      return "Documento institucional de laboratorio";
    }
    return java.util.stream.Stream.of(
            snapshot.organization().address(),
            snapshot.organization().documentLabel(),
            snapshot.organization().contacts().stream()
                .map(OrganizationDtos.ContactResponse::value)
                .collect(java.util.stream.Collectors.joining(" · ")))
        .filter(value -> value != null && !value.isBlank())
        .collect(java.util.stream.Collectors.joining("\n"));
  }

  private String authorizedSigner(OrganizationDtos.DocumentIdentity organization) {
    if (organization == null || organization.authorizedSigners().isEmpty()) {
      return "Responsable autorizado del laboratorio";
    }
    return organization.authorizedSigners().stream()
        .sorted(Comparator.comparingInt(OrganizationDtos.SignerResponse::displayOrder))
        .map(OrganizationDtos.SignerResponse::fullName)
        .findFirst()
        .orElse("Responsable autorizado del laboratorio");
  }

  private Color accent(LaboratoryReportDtos.Snapshot snapshot) {
    if (snapshot.organization() == null || snapshot.organization().primaryColor() == null) {
      return ACCENT;
    }
    try {
      return Color.decode(snapshot.organization().primaryColor());
    } catch (NumberFormatException ignored) {
      return ACCENT;
    }
  }

  private PdfPCell infoCell(String label, String value) {
    PdfPCell cell = cell(null, Rectangle.BOX, Color.WHITE, 7.5f);
    cell.setBorderColor(BORDER);
    cell.addElement(paragraph(label.toUpperCase(), 6.5f, Font.BOLD, MUTED));
    cell.addElement(paragraph(safe(value), 8.5f, Font.NORMAL, DARK));
    return cell;
  }

  private PdfPTable sectionBand(String value) {
    PdfPTable table = new PdfPTable(1);
    table.setWidthPercentage(100);
    PdfPCell cell = cell(null, Rectangle.BOX, SECTION, 5);
    cell.setBorderColor(DARK);
    Paragraph text = paragraph(value, 10, Font.BOLD, PRIMARY);
    text.setAlignment(Element.ALIGN_CENTER);
    cell.addElement(text);
    table.addCell(cell);
    return table;
  }

  private PdfPTable titleBand(String value) {
    PdfPTable table = new PdfPTable(1);
    table.setWidthPercentage(100);
    PdfPCell cell = cell(null, Rectangle.BOX, Color.WHITE, 7);
    cell.setBorderColor(DARK);
    Paragraph text = paragraph(value, 11, Font.BOLD, PRIMARY);
    text.setAlignment(Element.ALIGN_CENTER);
    cell.addElement(text);
    table.addCell(cell);
    return table;
  }

  private PdfPCell summaryCell(String label, String value) {
    PdfPCell cell = cell(null, Rectangle.BOTTOM, Color.WHITE, 5);
    cell.setBorderColor(BORDER);
    cell.addElement(paragraph(label.toUpperCase(), 6.5f, Font.BOLD, MUTED));
    cell.addElement(paragraph(safe(value), 8.5f, Font.BOLD, PRIMARY));
    return cell;
  }

  private PdfPCell testHeadingCell(String label, String value) {
    PdfPCell cell = cell(null, Rectangle.BOX, SURFACE, 7);
    cell.setBorderColor(BORDER);
    cell.addElement(paragraph(label.toUpperCase(), 6.5f, Font.BOLD, ACCENT));
    cell.addElement(paragraph(safe(value), 8.5f, Font.BOLD, PRIMARY));
    return cell;
  }

  private PdfPCell signatureCell(String label, String value) {
    PdfPCell cell = cell(null, Rectangle.TOP, Color.WHITE, 6);
    cell.setBorderColor(BORDER);
    cell.addElement(paragraph(label.toUpperCase(), 6.5f, Font.BOLD, MUTED));
    cell.addElement(paragraph(safe(value), 8, Font.NORMAL, DARK));
    return cell;
  }

  private PdfPCell noteCell(String label, String value) {
    PdfPCell cell = cell(null, Rectangle.BOX, ACCENT_SURFACE, 7);
    cell.setBorderColor(new Color(190, 226, 221));
    cell.addElement(paragraph(label.toUpperCase(), 6.5f, Font.BOLD, ACCENT));
    cell.addElement(paragraph(safe(value), 8, Font.NORMAL, DARK));
    return cell;
  }

  private PdfPCell tableHeader(String value) {
    PdfPCell cell = cell(null, Rectangle.BOX, SECTION, 6.5f);
    cell.setBorderColor(DARK);
    cell.setPhrase(new Phrase(safe(value), font(7.5f, Font.BOLD, PRIMARY)));
    cell.setHorizontalAlignment(Element.ALIGN_CENTER);
    return cell;
  }

  private PdfPCell tableBody(String value) {
    PdfPCell cell = cell(null, Rectangle.BOX, Color.WHITE, 6.5f);
    cell.setBorderColor(BORDER);
    cell.setPhrase(new Phrase(safe(value), font(8, Font.NORMAL, DARK)));
    return cell;
  }

  private PdfPCell rowNumberCell(int row) {
    PdfPCell cell = cell(null, Rectangle.BOX, SURFACE, 6.5f);
    cell.setBorderColor(BORDER);
    cell.setHorizontalAlignment(Element.ALIGN_CENTER);
    cell.setPhrase(new Phrase(String.valueOf(row), font(8, Font.BOLD, PRIMARY)));
    return cell;
  }

  private PdfPCell cell(String value, int border, Color background, float padding) {
    PdfPCell cell = new PdfPCell(value == null ? new Phrase() : new Phrase(value));
    cell.setBorder(border);
    cell.setBackgroundColor(background);
    cell.setPadding(padding);
    cell.setVerticalAlignment(Element.ALIGN_MIDDLE);
    return cell;
  }

  private Paragraph sectionTitle(String value) {
    Paragraph paragraph = paragraph(value, 10.5f, Font.BOLD, PRIMARY);
    paragraph.setSpacingBefore(3);
    paragraph.setSpacingAfter(7);
    return paragraph;
  }

  private Paragraph paragraph(String value, float size, int style, Color color) {
    Paragraph paragraph = new Paragraph(safe(value), font(size, style, color));
    paragraph.setLeading(size * 1.35f);
    return paragraph;
  }

  private Font font(float size, int style, Color color) {
    return FontFactory.getFont(FontFactory.HELVETICA, BaseFont.WINANSI, false, size, style, color);
  }

  private String valueLabel(LaboratoryReportDtos.Value value) {
    String label =
        value.fieldKind() == ResultFieldKind.CALCULATED
            ? value.label() + " (calculado)"
            : value.label();
    return value.repetition() > 1 ? "%s - lectura %d".formatted(label, value.repetition()) : label;
  }

  private String resultValue(LaboratoryReportDtos.Value value) {
    if (value.numericValue() != null) {
      return decimal(value.numericValue());
    }
    return safe(value.textValue());
  }

  private String matrixValue(LaboratoryReportDtos.MatrixCell cell) {
    if (cell.numericValue() != null) {
      return decimal(cell.numericValue());
    }
    if (cell.textValue() != null) {
      return cell.textValue();
    }
    return safe(cell.category());
  }

  private String decimal(BigDecimal value) {
    return value.stripTrailingZeros().toPlainString();
  }

  private String decimalOrDash(BigDecimal value) {
    return value == null ? "-" : decimal(value);
  }

  private String safe(String value) {
    return Objects.requireNonNullElse(value, "-");
  }

  private String statusLabel(LaboratoryReportStatus status) {
    return switch (status) {
      case DRAFT -> "BORRADOR";
      case ISSUED -> "EMITIDO";
      case CANCELLED -> "ANULADO";
    };
  }

  private static final class ReportPageEvent extends PdfPageEventHelper {
    private final String reportCode;
    private final String checksum;
    private final String organizationName;

    private ReportPageEvent(String reportCode, String checksum, String organizationName) {
      this.reportCode = reportCode;
      this.checksum = checksum;
      this.organizationName = organizationName;
    }

    @Override
    public void onEndPage(PdfWriter writer, Document document) {
      Font footerFont = FontFactory.getFont(FontFactory.HELVETICA, 7, MUTED);
      ColumnText.showTextAligned(
          writer.getDirectContent(),
          Element.ALIGN_LEFT,
          new Phrase(reportCode + " | " + checksum.substring(0, 12), footerFont),
          document.left(),
          25,
          0);
      ColumnText.showTextAligned(
          writer.getDirectContent(),
          Element.ALIGN_RIGHT,
          new Phrase(organizationName + " | Página " + writer.getPageNumber(), footerFont),
          document.right(),
          25,
          0);
    }
  }
}
