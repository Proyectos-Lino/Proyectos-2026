package com.labcore.platform.reports;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class LaboratoryReportLifecycleTest {
  @Test
  void preservesPdfContentAndTracksControlledLifecycle() {
    byte[] sourcePdf = {'%', 'P', 'D', 'F'};
    LaboratoryReport report =
        new LaboratoryReport(
            "IR-2026-0001",
            UUID.randomUUID(),
            1,
            "{}",
            sourcePdf,
            "checksum",
            "validator@example.test");
    sourcePdf[0] = 'X';

    report.issue("manager@example.test");

    assertThat(report.getStatus()).isEqualTo(LaboratoryReportStatus.ISSUED);
    assertThat(report.getIssuedBy()).isEqualTo("manager@example.test");
    assertThat(report.getIssuedAt()).isNotNull();
    assertThat(report.getPdfContent()).startsWith('%', 'P', 'D', 'F');

    report.cancel("manager@example.test", "Corrección requerida");

    assertThat(report.getStatus()).isEqualTo(LaboratoryReportStatus.CANCELLED);
    assertThat(report.getCancellationReason()).isEqualTo("Corrección requerida");
    assertThat(report.getCancelledAt()).isNotNull();
  }
}
