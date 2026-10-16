package com.odontocare.reports.service;

import com.odontocare.reports.dto.ReportContracts.*;
import com.odontocare.reports.model.ReportType;
import java.util.*;
import org.springframework.stereotype.Component;

@Component
public class ReportDefinitions {
  private static Column c(String key, String label, String kind) {
    return new Column(key, label, kind);
  }

  private static final Map<String, String> APPOINTMENT_STATES =
      Map.of(
          "RESERVED",
          "Reservada",
          "CONFIRMED",
          "Confirmada",
          "WAITING",
          "En espera",
          "IN_PROGRESS",
          "En atención",
          "ATTENDED",
          "Atendida",
          "CANCELLED",
          "Cancelada",
          "NO_SHOW",
          "No asistió");
  private static final List<Column> APPOINTMENT_COLUMNS =
      List.of(
          c("date", "Fecha y hora", "DATE_TIME"),
          c("patient", "Paciente", "TEXT"),
          c("dentist", "Profesional", "TEXT"),
          c("description", "Servicio", "TEXT"),
          c("status", "Estado", "STATE"),
          c("minutes", "Duración (min)", "COUNT"));

  public Definition get(ReportType type) {
    return switch (type) {
      case APPOINTMENTS ->
          new Definition(
              type,
              "Citas",
              "Citas según su horario y estado actual. Una reserva no representa trabajo realizado"
                  + " ni un cobro.",
              APPOINTMENT_COLUMNS,
              "date",
              true,
              true,
              false,
              false,
              APPOINTMENT_STATES);
      case ABSENCES ->
          new Definition(
              type,
              "Inasistencias",
              "Citas marcadas como no asistió. Las cancelaciones se consultan por separado en el"
                  + " reporte de citas.",
              APPOINTMENT_COLUMNS,
              "date",
              true,
              true,
              false,
              false,
              Map.of("NO_SHOW", "No asistió"));
      case TREATMENTS ->
          new Definition(
              type,
              "Presupuestos y avance",
              "Planes con actividad en el período. Estado actual e importes acordados; avance y"
                  + " cargos registrados hasta la fecha de corte.",
              List.of(
                  c("date", "Creado", "DATE_TIME"),
                  c("code", "Presupuesto", "TEXT"),
                  c("patient", "Paciente", "TEXT"),
                  c("dentist", "Profesional", "TEXT"),
                  c("description", "Plan de tratamiento", "TEXT"),
                  c("status", "Estado actual", "STATE"),
                  c("currency", "Moneda", "TEXT"),
                  c("budget", "Presupuestado", "MONEY"),
                  c("charged", "Deuda neta del plan", "MONEY"),
                  c("completed", "Sesiones realizadas", "COUNT"),
                  c("totalSessions", "Sesiones previstas", "COUNT"),
                  c("periodSessions", "Sesiones del período", "COUNT")),
              "date",
              true,
              true,
              true,
              false,
              Map.of(
                  "DRAFT",
                  "Borrador",
                  "PROPOSED",
                  "Propuesto",
                  "ACCEPTED",
                  "Aceptado",
                  "IN_PROGRESS",
                  "En curso",
                  "FINISHED",
                  "Finalizado",
                  "CANCELLED",
                  "Cancelado"));
      case SERVICES ->
          new Definition(
              type,
              "Servicios realizados",
              "Procedimientos de la última versión de atenciones finalizadas. Las sesiones"
                  + " incluidas en un plan no son cargos ni cobros nuevos.",
              List.of(
                  c("date", "Atención", "DATE"),
                  c("patient", "Paciente", "TEXT"),
                  c("dentist", "Profesional", "TEXT"),
                  c("service", "Servicio", "TEXT"),
                  c("description", "Procedimiento", "TEXT"),
                  c("status", "Modalidad", "STATE"),
                  c("units", "Unidades individuales", "COUNT"),
                  c("planSessions", "Sesiones de plan", "COUNT"),
                  c("tooth", "Pieza", "TEXT")),
              "date",
              true,
              true,
              false,
              false,
              Map.of("INDIVIDUAL", "Servicio individual", "PLAN", "Sesión de plan"));
      case COLLECTIONS ->
          new Definition(
              type,
              "Cobros",
              "Pagos recibidos y sus devoluciones/reversiones en el período, por consultorio."
                  + " Aplicar un anticipo no genera otro ingreso.",
              List.of(
                  c("date", "Fecha", "DATE"),
                  c("patient", "Paciente", "TEXT"),
                  c("status", "Movimiento", "STATE"),
                  c("description", "Detalle", "TEXT"),
                  c("currency", "Moneda", "TEXT"),
                  c("amount", "Importe neto", "MONEY"),
                  c("method", "Medio", "TEXT"),
                  c("reference", "Referencia", "TEXT"),
                  c("receipt", "Constancia", "TEXT")),
              "date",
              false,
              true,
              true,
              true,
              Map.of("PAYMENT", "Pago", "REFUND", "Devolución", "REVERSAL", "Reversión"));
      case CHARGES ->
          new Definition(
              type,
              "Cargos emitidos",
              "Obligaciones de pago registradas en el período. Ajustes y anulaciones conservan su"
                  + " signo y origen; estos importes no son dinero cobrado.",
              List.of(
                  c("date", "Emitido", "DATE_TIME"),
                  c("patient", "Paciente", "TEXT"),
                  c("dentist", "Profesional", "TEXT"),
                  c("status", "Origen", "STATE"),
                  c("description", "Tratamiento", "TEXT"),
                  c("currency", "Moneda", "TEXT"),
                  c("amount", "Importe", "MONEY")),
              "date",
              true,
              true,
              true,
              false,
              Map.of(
                  "PLAN",
                  "Plan aceptado",
                  "SERVICE",
                  "Servicio realizado",
                  "ADJUSTMENT",
                  "Ajuste",
                  "CANCELLATION",
                  "Anulación"));
      case EXPENSES ->
          new Definition(
              type,
              "Egresos",
              "Egresos y sus reversiones por fecha de operación. Los importes negativos descuentan"
                  + " una reversión del gasto.",
              List.of(
                  c("date", "Fecha", "DATE"),
                  c("status", "Movimiento", "STATE"),
                  c("description", "Concepto", "TEXT"),
                  c("category", "Categoría", "TEXT"),
                  c("supplier", "Proveedor", "TEXT"),
                  c("currency", "Moneda", "TEXT"),
                  c("amount", "Egreso neto", "MONEY"),
                  c("method", "Medio", "TEXT"),
                  c("reference", "Referencia", "TEXT")),
              "date",
              false,
              false,
              true,
              true,
              Map.of("EXPENSE", "Egreso", "EXPENSE_REVERSAL", "Reversión de egreso"));
      case BALANCES ->
          new Definition(
              type,
              "Saldos de pacientes",
              "Cuenta acumulada a la fecha final del período, incluyendo deuda anterior. Solo"
                  + " cuenta movimientos y aplicaciones registrados hasta ese corte; los anticipos"
                  + " se muestran separados.",
              List.of(
                  c("patient", "Paciente", "TEXT"),
                  c("code", "Código", "TEXT"),
                  c("currency", "Moneda", "TEXT"),
                  c("debt", "Deuda neta", "MONEY"),
                  c("received", "Dinero recibido neto", "MONEY"),
                  c("applied", "Aplicado", "MONEY"),
                  c("pending", "Saldo pendiente", "MONEY"),
                  c("advance", "Anticipo disponible", "MONEY")),
              "patient",
              false,
              true,
              true,
              false,
              Map.of());
      case INSTALLMENTS ->
          new Definition(
              type,
              "Cuotas",
              "Cuotas con vencimiento en el período y pagos aplicados al corte. Las cuotas"
                  + " distribuyen deuda existente; no se suman otra vez a los cargos.",
              List.of(
                  c("date", "Vencimiento", "DATE"),
                  c("patient", "Paciente", "TEXT"),
                  c("description", "Tratamiento", "TEXT"),
                  c("position", "Cuota", "COUNT"),
                  c("status", "Estado al corte", "STATE"),
                  c("currency", "Moneda", "TEXT"),
                  c("amount", "Importe de cuota", "MONEY"),
                  c("paid", "Pagado", "MONEY"),
                  c("pending", "Pendiente", "MONEY")),
              "date",
              false,
              true,
              true,
              false,
              Map.of(
                  "PENDING",
                  "Pendiente",
                  "PARTIAL",
                  "Parcial",
                  "PAID",
                  "Pagada",
                  "OVERDUE",
                  "Vencida",
                  "REVIEW",
                  "Revisar distribución"));
    };
  }
}
