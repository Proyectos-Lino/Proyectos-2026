package com.odontocare.finance.service;

import static com.odontocare.shared.pdf.PdfContent.*;

import com.odontocare.clinical.service.ClinicalAccess;
import com.odontocare.finance.dto.PaymentContracts.Allocation;
import com.odontocare.finance.model.*;
import com.odontocare.finance.repository.*;
import com.odontocare.installation.repository.InstallationProfileRepository;
import com.odontocare.patients.repository.PatientRepository;
import com.odontocare.shared.pagination.*;
import com.odontocare.shared.pdf.PdfContent;
import com.odontocare.shared.web.ApiException;
import java.math.*;
import java.text.NumberFormat;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;

@Service
public class FinancialPdfContentService {
  private final ChargeEntryRepository charges;
  private final MoneyMovementRepository movements;
  private final MoneyApplicationRepository applications;
  private final PatientRepository patients;
  private final InstallmentService installments;
  private final InstallationProfileRepository profiles;
  private final ClinicalAccess access;

  public FinancialPdfContentService(
      ChargeEntryRepository charges,
      MoneyMovementRepository movements,
      MoneyApplicationRepository applications,
      PatientRepository patients,
      InstallmentService installments,
      InstallationProfileRepository profiles,
      ClinicalAccess access) {
    this.charges = charges;
    this.movements = movements;
    this.applications = applications;
    this.patients = patients;
    this.installments = installments;
    this.profiles = profiles;
    this.access = access;
  }

  public PdfContent receipt(MoneyMovement movement, List<Allocation> allocations, String reason) {
    var patient = patients.findById(movement.getPatientId()).orElseThrow();
    var content =
        new PdfContent()
            .field("Paciente", patient.getFullName())
            .field("Código del paciente", patient.getCode())
            .field("Fecha del movimiento", date(movement.getOccurredOn()))
            .field("Responsable", movement.getActorName())
            .field("Movimiento", kind(movement.getKind()))
            .field("Medio de pago", method(movement.getMethod()))
            .field("Moneda", movement.getCurrency())
            .field("Referencia", movement.getReference());
    if (movement.getOriginalId() != null)
      content.field(
          "Constancia original",
          movements.findById(movement.getOriginalId()).orElseThrow().getReceiptCode());
    content.total("Importe del movimiento", number(movement.getAmount()));
    var rows = new ArrayList<List<String>>();
    BigDecimal allocated = BigDecimal.ZERO;
    for (var allocation : allocations) {
      rows.add(
          List.of(
              charges.findById(allocation.chargeId()).orElseThrow().getDescription(),
              number(allocation.amount())));
      allocated = allocated.add(allocation.amount());
    }
    content.total(
        movement.getKind().equals("PAYMENT") ? "Aplicado a cargos" : "Aplicaciones liberadas",
        number(allocated));
    if (movement.getKind().equals("PAYMENT"))
      content.total("Anticipo al emitir", number(movement.getAmount().subtract(allocated)));
    content.text("Descripción del movimiento", movement.getDescription());
    content.table(
        movement.getKind().equals("PAYMENT")
            ? "Detalle de aplicación del pago"
            : "Detalle de liberaciones",
        List.of(column("Tratamiento / cargo", 4), amount("Importe", 1.2f)),
        rows);
    content
        .text("Motivo", reason)
        .text("Naturaleza del documento", "Constancia interna. Sin facturación electrónica.");
    return content;
  }

  public PdfContent statement(UUID patientId, String currency) {
    var patient = patients.findById(patientId).orElseThrow();
    var content =
        new PdfContent()
            .field("Paciente", patient.getFullName())
            .field("Código del paciente", patient.getCode())
            .field("Fecha de emisión", date(access.today()))
            .field("Preparado por", access.actor().getDisplayName())
            .field("Moneda", currency)
            .field("Alcance", "Historial completo, incluidos los cargos finalizados");
    var debt = charges.charges(patientId, currency).add(charges.adjustments(patientId, currency));
    var applied = applications.applied(patientId, currency);
    var received = movements.received(patientId, currency);
    content
        .total("Deuda generada", number(debt))
        .total("Dinero recibido neto", number(received))
        .total("Aplicado a cargos", number(applied))
        .total("Saldo pendiente", number(debt.subtract(applied)))
        .total("Anticipo disponible", number(received.subtract(applied)));
    var chargeSpec =
        SearchSpecifications.<ChargeEntry>text("")
            .and(SearchSpecifications.equal("patientId", patientId))
            .and(SearchSpecifications.equal("currency", currency));
    if (charges.count(chargeSpec) > 5000)
      throw ApiException.badRequest("El estado supera 5000 movimientos de cargos.");
    var chargeRows = new ArrayList<List<String>>();
    for (int page = 0; ; page++) {
      var result = charges.findAll(chargeSpec, paging(page));
      for (var c : result)
        chargeRows.add(
            List.of(
                date(c.getCreatedAt()),
                chargeKind(c.getKind()),
                c.getDescription(),
                number(c.getAmount())));
      if (!result.hasNext()) break;
    }
    content.table(
        "Cargos y ajustes de deuda",
        List.of(
            column("Fecha", 1.1f),
            column("Origen", 1.45f),
            column("Tratamiento / detalle", 3.1f),
            amount("Importe", 1.2f)),
        chargeRows);
    var moneySpec =
        SearchSpecifications.<MoneyMovement>text("")
            .and(SearchSpecifications.equal("patientId", patientId))
            .and(SearchSpecifications.equal("currency", currency));
    if (movements.count(moneySpec) > 5000)
      throw ApiException.badRequest("El estado supera 5000 movimientos de dinero.");
    var moneyRows = new ArrayList<List<String>>();
    for (int page = 0; ; page++) {
      var result = movements.findAll(moneySpec, paging(page));
      for (var m : result)
        moneyRows.add(
            List.of(
                date(m.getOccurredOn()),
                kind(m.getKind()),
                Objects.toString(m.getReceiptCode(), "-"),
                method(m.getMethod()),
                number(m.getAmount())));
      if (!result.hasNext()) break;
    }
    content.table(
        "Pagos, devoluciones y reversiones",
        List.of(
            column("Fecha", 1.2f),
            column("Movimiento", 1.8f),
            column("Constancia", 1.6f),
            column("Medio", 1.3f),
            amount("Importe", 1.4f)),
        moneyRows);
    var appSpec =
        SearchSpecifications.<MoneyApplication>text("")
            .and(SearchSpecifications.equal("patientId", patientId));
    if (applications.count(appSpec) > 5000)
      throw ApiException.badRequest("El estado supera 5000 aplicaciones.");
    var applicationRows = new ArrayList<List<String>>();
    for (int page = 0; ; page++) {
      var result = applications.findAll(appSpec, paging(page));
      for (var a : result) {
        var c = charges.findById(a.getChargeId()).orElseThrow();
        if (c.getCurrency().equals(currency))
          applicationRows.add(
              List.of(
                  date(a.getCreatedAt()),
                  movements.findById(a.getPaymentId()).orElseThrow().getReceiptCode(),
                  c.getDescription(),
                  number(a.getAmount())));
      }
      if (!result.hasNext()) break;
    }
    content.table(
        "Aplicaciones y liberaciones",
        List.of(
            column("Fecha", 1.1f),
            column("Constancia", 1.5f),
            column("Cargo", 3.1f),
            amount("Importe aplicado", 1.3f)),
        applicationRows);
    var dueRows = new ArrayList<List<String>>();
    for (int page = 0; page < 51; page++) {
      var q = new PageQuery();
      q.setPage(page);
      q.setSize(100);
      q.setSort("dueOn");
      var result = installments.list(patientId, null, true, null, null, q);
      for (var due : result.items())
        if (due.currency().equals(currency))
          dueRows.add(
              List.of(
                  "Cuota " + due.position(),
                  date(due.dueOn()),
                  number(due.amount()),
                  number(due.paid()),
                  number(due.pending())));
      if (page + 1 >= result.totalPages()) break;
      if (page == 50) throw ApiException.badRequest("El estado supera 5000 cuotas.");
    }
    content.table(
        "Cuotas y vencimientos vigentes",
        List.of(
            column("Cuota", 1.1f),
            column("Vencimiento", 1.3f),
            amount("Importe", 1.1f),
            amount("Pagado", 1.1f),
            amount("Pendiente", 1.2f)),
        dueRows);
    content.text(
        "Lectura del estado de cuenta",
        "Los cargos representan deuda; los pagos representan dinero recibido. Las aplicaciones"
            + " distribuyen el dinero y no generan otro ingreso. Las cuotas tampoco añaden deuda."
            + " Los ajustes y liberaciones conservan su signo.");
    return content;
  }

  public PdfContent cash(CashSession session) {
    var content =
        new PdfContent()
            .field("Apertura", dateTime(session.getCreatedAt()))
            .field("Abierta por", session.getOpenedBy())
            .field("Cierre", dateTime(session.getClosedAt()))
            .field("Cerrada por", session.getClosedBy())
            .field("Moneda", session.getCurrency());
    content
        .total("Fondo inicial", number(session.getOpening()))
        .total("Efectivo esperado", number(session.getExpected()))
        .total("Efectivo contado", number(session.getCounted()))
        .total("Diferencia", number(session.getDifference()))
        .total("Otros medios (neto)", number(movements.nonCashNet(session.getId())));
    var spec =
        SearchSpecifications.<MoneyMovement>text("")
            .and(SearchSpecifications.equal("cashSessionId", session.getId()));
    if (movements.count(spec) > 5000)
      throw ApiException.badRequest("El arqueo supera 5000 movimientos.");
    var rows = new ArrayList<List<String>>();
    for (int page = 0; ; page++) {
      var result = movements.findAll(spec, paging(page));
      for (var m : result) {
        boolean incoming = Set.of("PAYMENT", "EXPENSE_REVERSAL").contains(m.getKind());
        rows.add(
            List.of(
                date(m.getOccurredOn()),
                kind(m.getKind()),
                m.getDescription(),
                method(m.getMethod()),
                incoming ? number(m.getAmount()) : "-",
                incoming ? "-" : number(m.getAmount())));
      }
      if (!result.hasNext()) break;
    }
    content.table(
        "Movimientos del turno",
        List.of(
            column("Fecha", 1.1f),
            column("Movimiento", 1.5f),
            column("Descripción", 2.8f),
            column("Medio", 1.6f),
            amount("Entrada", 1.1f),
            amount("Salida", 1.1f)),
        rows);
    content
        .text("Observaciones del cierre", session.getReason())
        .text(
            "Criterio del arqueo",
            "El efectivo esperado corresponde al fondo inicial más entradas y menos salidas en"
                + " efectivo. Los movimientos por otros medios se presentan por separado y no"
                + " aumentan el efectivo esperado.");
    return content;
  }

  private PageRequest paging(int page) {
    return PageRequest.of(page, 100, Sort.by("createdAt", "id"));
  }

  private String date(LocalDate value) {
    var format = profiles.findById((short) 1).orElseThrow().getDateFormat();
    return value.format(
        DateTimeFormatter.ofPattern(
            format.equals("MDY")
                ? "MM/dd/yyyy"
                : format.equals("YMD") ? "yyyy-MM-dd" : "dd/MM/yyyy"));
  }

  private String date(Instant value) {
    var zone = ZoneId.of(profiles.findById((short) 1).orElseThrow().getTimeZone());
    return date(value.atZone(zone).toLocalDate());
  }

  private String dateTime(Instant value) {
    var zone = ZoneId.of(profiles.findById((short) 1).orElseThrow().getTimeZone());
    return date(value) + " " + value.atZone(zone).format(DateTimeFormatter.ofPattern("HH:mm"));
  }

  private String number(BigDecimal value) {
    var f = NumberFormat.getNumberInstance(Locale.US);
    f.setMinimumFractionDigits(2);
    f.setMaximumFractionDigits(2);
    f.setRoundingMode(RoundingMode.UNNECESSARY);
    return f.format(value);
  }

  private String method(String value) {
    return Map.of(
            "CASH", "Efectivo", "TRANSFER", "Transferencia", "CARD", "Tarjeta", "OTHER", "Otro")
        .get(value);
  }

  private String kind(String value) {
    return Map.of(
            "PAYMENT",
            "Abono",
            "REFUND",
            "Devolución",
            "REVERSAL",
            "Reversión de pago",
            "EXPENSE",
            "Egreso",
            "EXPENSE_REVERSAL",
            "Reversión de egreso")
        .get(value);
  }

  private String chargeKind(String value) {
    return Map.of(
            "PLAN",
            "Cargo de plan",
            "SERVICE",
            "Cargo por servicio",
            "ADJUSTMENT",
            "Ajuste",
            "CANCELLATION",
            "Cancelación")
        .get(value);
  }
}
