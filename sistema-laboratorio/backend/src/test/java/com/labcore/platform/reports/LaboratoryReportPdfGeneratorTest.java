package com.labcore.platform.reports;

import static org.assertj.core.api.Assertions.assertThat;

import com.labcore.platform.catalog.ResultFieldKind;
import com.labcore.platform.organization.OrganizationDtos;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class LaboratoryReportPdfGeneratorTest {
  @Test
  void generatesARealPdfWithValidatedResultData() throws Exception {
    LaboratoryReportDtos.Snapshot snapshot =
        new LaboratoryReportDtos.Snapshot(
            "IR-2026-0001",
            1,
            Instant.parse("2026-09-08T15:00:00Z"),
            new OrganizationDtos.DocumentIdentity(
                "Laboratorio de prueba",
                "Laboratorio de prueba S.A.C.",
                "RUC 20123456789",
                "Dirección institucional",
                null,
                "#0F766E",
                "#0F172A",
                "America/Lima",
                "Documento emitido bajo control del laboratorio.",
                "Acreditación de prueba",
                "LAB-001",
                "Alcance de prueba",
                List.of(),
                List.of()),
            "OS-2026-0001",
            "Productor de prueba",
            "RUC 20123456789",
            "Destinatario",
            "informe@example.test",
            "Dirección de prueba",
            "Análisis fisicoquímico",
            "Sin observaciones",
            List.of(
                new LaboratoryReportDtos.SampleResult(
                    1,
                    "AL-2026-0001",
                    "M-01",
                    "Productor asociado",
                    "RUC 20999999999",
                    "Producto",
                    null,
                    "Alimento",
                    "Muestra homogénea",
                    "Ica",
                    "Planta",
                    Instant.parse("2026-09-07T16:00:00Z"),
                    new java.math.BigDecimal("2"),
                    "kg",
                    "Bolsa sellada",
                    "L-01",
                    "Marca",
                    null,
                    List.of(
                        new LaboratoryReportDtos.TestResult(
                            "Sólidos disueltos",
                            "Gravimetría",
                            "Analista",
                            null,
                            "Validador",
                            Instant.parse("2026-09-08T16:00:00Z"),
                            List.of(
                                new LaboratoryReportDtos.Value(
                                    "Humedad",
                                    1,
                                    null,
                                    new BigDecimal("8.4"),
                                    "g",
                                    ResultFieldKind.CAPTURED,
                                    null,
                                    null),
                                new LaboratoryReportDtos.Value(
                                    "Humedad",
                                    2,
                                    null,
                                    new BigDecimal("8.6"),
                                    "g",
                                    ResultFieldKind.CAPTURED,
                                    null,
                                    null),
                                new LaboratoryReportDtos.Value(
                                    "Humedad promedio",
                                    1,
                                    null,
                                    new BigDecimal("8.5"),
                                    "g",
                                    ResultFieldKind.CALCULATED,
                                    "ROUND(AVG(hum), 2)",
                                    "{\"hum\":[8.4,8.6]}")),
                            new LaboratoryReportDtos.Matrix(
                                2,
                                2,
                                List.of(
                                    new LaboratoryReportDtos.MatrixColumn(
                                        0,
                                        "Cargador 1",
                                        new BigDecimal("5"),
                                        new BigDecimal("7"),
                                        "Circular"),
                                    new LaboratoryReportDtos.MatrixColumn(
                                        1,
                                        "Cargador 2",
                                        new BigDecimal("7"),
                                        new BigDecimal("5"),
                                        "Ovalada")),
                                List.of(
                                    matrixCell(0, 0, "Categoría uno"),
                                    matrixCell(0, 1, "Categoría tres"),
                                    matrixCell(1, 0, "Categoría dos"),
                                    matrixCell(1, 1, "Categoría tres")),
                                List.of(
                                    new LaboratoryReportDtos.MatrixCategorySummary(
                                        "Categoría uno", 1, new BigDecimal("25")),
                                    new LaboratoryReportDtos.MatrixCategorySummary(
                                        "Categoría dos", 1, new BigDecimal("25")),
                                    new LaboratoryReportDtos.MatrixCategorySummary(
                                        "Categoría tres", 2, new BigDecimal("50")))))))));

    byte[] pdf =
        new LaboratoryReportPdfGenerator()
            .generate(
                snapshot,
                LaboratoryReportStatus.ISSUED,
                "b629bb620e1f4f6be88ef15b7e89a6de34ac7324d6c9af5e438b1f4e70db5799");

    String previewPath = System.getProperty("labcore.report.preview");
    if (previewPath != null) {
      Path output = Path.of(previewPath);
      Files.createDirectories(output.getParent());
      Files.write(output, pdf);
    }

    assertThat(pdf).startsWith('%', 'P', 'D', 'F');
    assertThat(pdf.length).isGreaterThan(2_000);
  }

  private LaboratoryReportDtos.MatrixCell matrixCell(int row, int column, String category) {
    return new LaboratoryReportDtos.MatrixCell(row, column, null, null, category);
  }
}
