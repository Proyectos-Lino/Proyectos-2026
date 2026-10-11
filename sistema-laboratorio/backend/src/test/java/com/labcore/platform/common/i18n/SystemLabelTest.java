package com.labcore.platform.common.i18n;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class SystemLabelTest {
  @Test
  void translatesCodesAndEmbeddedWorkflowValues() {
    assertThat(SystemLabel.forCode("REPORT_DOWNLOADED")).isEqualTo("Informe descargado");
    assertThat(SystemLabel.localizeText("Estado: RECORDED → VALIDATED. Rol: ANALYST"))
        .isEqualTo("Estado: Registrado → Validado. Rol: Analista");
  }
}
