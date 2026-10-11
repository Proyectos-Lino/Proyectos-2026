package com.labcore.platform.common.i18n;

import java.util.Comparator;
import java.util.Map;
import java.util.regex.Pattern;

public final class SystemLabel {
  private static final Map<String, String> LABELS =
      Map.ofEntries(
          Map.entry("ADMIN", "Administrador"),
          Map.entry("COMMERCIAL", "Atención comercial"),
          Map.entry("TECHNICAL_MANAGER", "Responsable técnico"),
          Map.entry("ANALYST", "Analista"),
          Map.entry("VALIDATOR", "Validador"),
          Map.entry("SCALAR", "Escalar"),
          Map.entry("REPEATABLE", "Varias lecturas"),
          Map.entry("MATRIX", "Matriz"),
          Map.entry("NUMERIC", "Numérico"),
          Map.entry("TEXT", "Texto"),
          Map.entry("QUALITATIVE", "Cualitativo"),
          Map.entry("CAPTURED", "Capturado"),
          Map.entry("CALCULATED", "Calculado"),
          Map.entry("INFORMATIONAL", "Informativo"),
          Map.entry("RUC", "RUC"),
          Map.entry("DNI", "DNI"),
          Map.entry("PASSPORT", "Pasaporte"),
          Map.entry("OTHER", "Otro documento"),
          Map.entry("CLIENT", "Cliente"),
          Map.entry("PRODUCER", "Productor"),
          Map.entry("BOTH", "Cliente y productor"),
          Map.entry("DRAFT", "Borrador"),
          Map.entry("PENDING", "Pendiente"),
          Map.entry("ASSIGNED", "Asignado"),
          Map.entry("COMPLETED", "Completado"),
          Map.entry("IN_PROGRESS", "En proceso"),
          Map.entry("COMMERCIAL_CORRECTION", "En corrección comercial"),
          Map.entry("CORRECTION_REVIEW", "Corrección en revisión"),
          Map.entry("RECORDED", "Registrado"),
          Map.entry("CORRECTION_REQUESTED", "Corrección solicitada"),
          Map.entry("CORRECTED", "Corregido"),
          Map.entry("RESULTS_RECORDED", "Resultados registrados"),
          Map.entry("VALIDATED", "Validado"),
          Map.entry("FINALIZED", "Finalizado"),
          Map.entry("CLOSED", "Cerrado"),
          Map.entry("CANCELLED", "Anulado"),
          Map.entry("INVALIDATED", "Invalidado"),
          Map.entry("ISSUED", "Emitido"),
          Map.entry("APPROVED", "Aprobado"),
          Map.entry("REISSUED", "Reemitido"),
          Map.entry("CREATED", "Creación"),
          Map.entry("UPDATED", "Actualización"),
          Map.entry("CONFIRMED", "Confirmación"),
          Map.entry("COMMERCIAL_DETAILS_UPDATED", "Datos comerciales actualizados"),
          Map.entry("SAMPLES_UPDATED", "Muestras actualizadas"),
          Map.entry("COMMERCIAL_CORRECTION_REQUESTED", "Devolución a Comercial"),
          Map.entry("COMMERCIAL_CORRECTION_SUBMITTED", "Corrección enviada a revisión"),
          Map.entry("COMMERCIAL_CORRECTION_APPROVED", "Corrección comercial aprobada"),
          Map.entry("COMMERCIAL_CORRECTION_PAUSED", "Operación técnica pausada"),
          Map.entry("COMMERCIAL_CORRECTION_RESUMED", "Operación técnica reanudada"),
          Map.entry("TECHNICAL_DATA_INVALIDATED", "Datos técnicos invalidados"),
          Map.entry("SAMPLES_CLASSIFIED", "Muestras clasificadas"),
          Map.entry("PROCESSING_STARTED", "Procesamiento iniciado"),
          Map.entry("STATUS_UPDATED", "Estado actualizado"),
          Map.entry("RESULT_RECORDED", "Resultado registrado"),
          Map.entry("RESULT_CORRECTION_REQUESTED", "Corrección solicitada"),
          Map.entry("RESULT_RESUBMITTED", "Resultado corregido y reenviado"),
          Map.entry("RESULT_CORRECTED_AND_VALIDATED", "Resultado corregido y validado"),
          Map.entry("RESULT_VALIDATED", "Resultado validado"),
          Map.entry("REGISTERED", "Registro de resultado"),
          Map.entry("CORRECTED_AND_VALIDATED", "Corrección y validación"),
          Map.entry("PROFILE_UPDATED", "Configuración actualizada"),
          Map.entry("REPORT_VIEWED", "Informe consultado"),
          Map.entry("REPORT_DOWNLOADED", "Informe descargado"),
          Map.entry("REPORT_ISSUED", "Informe emitido"),
          Map.entry("REPORT_CANCELLED", "Informe anulado"),
          Map.entry("REPORT_REISSUED", "Informe reemitido"),
          Map.entry("REPORT_PREPARED", "Informe preparado"),
          Map.entry("RESULT_CORRECTED", "Resultado corregido"),
          Map.entry("MATRIX_CORRECTED", "Matriz corregida"),
          Map.entry("RESULT_RECALCULATED", "Resultado recalculado"));

  private SystemLabel() {}

  public static String forCode(String code) {
    if (code == null || code.isBlank()) {
      return code;
    }
    return LABELS.getOrDefault(code, sentenceCase(code));
  }

  public static String forValue(Enum<?> value) {
    return forCode(value.name());
  }

  public static String localizeText(String text) {
    if (text == null || text.isBlank()) {
      return text;
    }
    return LABELS.entrySet().stream()
        .sorted(
            Map.Entry.<String, String>comparingByKey(
                Comparator.comparingInt(String::length).reversed()))
        .reduce(
            text,
            (localized, entry) ->
                localized.replaceAll(
                    "\\b" + Pattern.quote(entry.getKey()) + "\\b", entry.getValue()),
            (first, second) -> first);
  }

  private static String sentenceCase(String code) {
    String normalized =
        code.replace('_', ' ').toLowerCase(java.util.Locale.forLanguageTag("es-PE"));
    return Character.toUpperCase(normalized.charAt(0)) + normalized.substring(1);
  }
}
