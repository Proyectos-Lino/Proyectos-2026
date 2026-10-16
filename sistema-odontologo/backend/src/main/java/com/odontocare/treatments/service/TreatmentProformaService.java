package com.odontocare.treatments.service;

import com.odontocare.audit.service.AuditService;
import com.odontocare.clinical.service.ClinicalAccess;
import com.odontocare.installation.repository.*;
import com.odontocare.shared.pagination.*;
import com.odontocare.shared.pdf.*;
import com.odontocare.shared.web.ApiException;
import com.odontocare.treatments.repository.*;
import com.odontocare.treatments.repository.TreatmentProformaRepository.Document;
import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TreatmentProformaService {
  private final TreatmentPlanRepository plans;
  private final PlanItemRepository items;
  private final TreatmentPlanningRepository planning;
  private final TreatmentProformaRepository documents;
  private final InstallationProfileRepository profiles;
  private final InstallationLogoRepository logos;
  private final InstitutionalPdfRenderer renderer;
  private final SessionDates dates;
  private final ClinicalAccess access;
  private final AuditService audit;
  private final Clock clock;

  public TreatmentProformaService(
      TreatmentPlanRepository plans,
      PlanItemRepository items,
      TreatmentPlanningRepository planning,
      TreatmentProformaRepository documents,
      InstallationProfileRepository profiles,
      InstallationLogoRepository logos,
      InstitutionalPdfRenderer renderer,
      SessionDates dates,
      ClinicalAccess access,
      AuditService audit,
      Clock clock) {
    this.plans = plans;
    this.items = items;
    this.planning = planning;
    this.documents = documents;
    this.profiles = profiles;
    this.logos = logos;
    this.renderer = renderer;
    this.dates = dates;
    this.access = access;
    this.audit = audit;
    this.clock = clock;
  }

  @Transactional
  public Document emit(UUID id) {
    var plan = plans.lockById(id).orElseThrow(ApiException::notFound);
    var prior = documents.version(id, plan.getVersion());
    if (prior.isPresent()) return prior.get();
    var profile = profiles.findById((short) 1).orElseThrow();
    var content =
        new PdfContent()
            .field("Paciente", plan.getPatientName())
            .field("Profesional", plan.getDentistName())
            .field("Tratamiento", plan.getTitle())
            .field("Estado", state(plan.getStatus()))
            .field("Fecha de emisión", date(access.today()))
            .field("Versión", plan.getVersion());
    if (plan.getAcceptedAt() != null)
      content
          .field("Aceptado por", plan.getAcceptedBy())
          .field(
              "Fecha de aceptación",
              date(plan.getAcceptedAt().atZone(ZoneId.of(profile.getTimeZone())).toLocalDate()));
    var rows = new ArrayList<List<String>>();
    var calendar = new ArrayList<List<String>>();
    var total = BigDecimal.ZERO;
    for (var item : items.findByPlanIdOrderByPosition(id)) {
      var amount = item.getUnitPrice().multiply(BigDecimal.valueOf(item.getQuantity()));
      total = total.add(amount);
      rows.add(
          List.of(
              item.getDescription(),
              item.getTooth() == null ? "—" : item.getTooth().toString(),
              Integer.toString(item.getQuantity()),
              Integer.toString(item.getSessions()),
              money(item.getUnitPrice(), plan.getCurrency()),
              money(amount, plan.getCurrency())));
      var scheduling = planning.scheduling(item.getId());
      if (scheduling.mode().equals("FREQUENCY")) {
        String unit =
            switch (scheduling.unit()) {
              case "DAYS" -> scheduling.interval() == 1 ? "día" : "días";
              case "WEEKS" -> scheduling.interval() == 1 ? "semana" : "semanas";
              default -> scheduling.interval() == 1 ? "mes" : "meses";
            };
        content.text(
            "Frecuencia · " + item.getDescription(),
            "Cada "
                + scheduling.interval()
                + " "
                + unit
                + ", desde "
                + date(scheduling.firstDate())
                + ".");
      }
      var calculated = dates.calculate(scheduling, item.getSessions());
      if (scheduling.mode().equals("NONE"))
        calendar.add(
            List.of(
                item.getDescription(),
                "1–" + item.getSessions(),
                "Fechas por coordinar",
                planning.duration(item.getId()) + " min"));
      else
        for (int index = 0; index < calculated.size(); index++)
          calendar.add(
              List.of(
                  item.getDescription(),
                  Integer.toString(index + 1),
                  calculated.get(index) == null
                      ? "Fecha por coordinar"
                      : date(calculated.get(index)) + " · Hora por coordinar",
                  planning.duration(item.getId()) + " min"));
    }
    content.total("Total de los tratamientos", money(total, plan.getCurrency()));
    if (plan.getAcceptedTotal() != null)
      content.total(
          "Importe original aceptado", money(plan.getAcceptedTotal(), plan.getCurrency()));
    content.table(
        "Tratamientos e importes acordados",
        List.of(
            PdfContent.column("Tratamiento", 2.8f),
            PdfContent.column("Pieza", .6f),
            PdfContent.column("Unidades", .9f),
            PdfContent.column("Sesiones", .9f),
            PdfContent.amount("Precio unitario", 1.2f),
            PdfContent.amount("Importe", 1.2f)),
        rows);
    content.table(
        "Sesiones y fechas previstas",
        List.of(
            PdfContent.column("Tratamiento", 2.5f),
            PdfContent.column("Sesión", .6f),
            PdfContent.column("Fecha prevista", 2.5f),
            PdfContent.column("Duración", .8f)),
        calendar);
    var agreement = planning.agreement(id);
    var payments = new ArrayList<List<String>>();
    if (agreement.initialAmount().signum() > 0)
      payments.add(
          List.of(
              "Pago inicial",
              "Al aceptar el plan",
              money(agreement.initialAmount(), plan.getCurrency()),
              planning.payment(id) == null
                  ? "Acordado; todavía no cobrado"
                  : "Registrado al aceptar"));
    int number = 0;
    for (var due : agreement.installments())
      payments.add(
          List.of(
              "Cuota " + (++number),
              date(due.dueOn()),
              money(due.amount(), plan.getCurrency()),
              "Vencimiento acordado"));
    if (payments.isEmpty())
      content.text(
          "Acuerdo de pago",
          "No se ha pactado un pago inicial ni un calendario de cuotas en este documento.");
    else
      content.table(
          "Acuerdo de pago",
          List.of(
              PdfContent.column("Pago acordado", 1.2f),
              PdfContent.column("Fecha o condición", 1.4f),
              PdfContent.amount("Importe", 1),
              PdfContent.column("Detalle", 2)),
          payments);
    content.text(
        "Condiciones acordadas",
        plan.getConditions().isBlank()
            ? "Sin condiciones adicionales registradas."
            : plan.getConditions());
    content.text(
        "Coordinación de sesiones",
        "Las fechas previstas se coordinarán con el consultorio. Una sesión sin hora no reserva un"
            + " intervalo; la hora se confirma según disponibilidad. Las cuotas de pago no generan"
            + " nuevos cargos por el mismo tratamiento.");
    content.text(
        "Conformidad y firmas",
        "He revisado los tratamientos, importes, sesiones y condiciones indicados.");
    content.table(
        "Firmas manuscritas",
        List.of(
            PdfContent.column("Paciente o responsable", 1), PdfContent.column("Profesional", 1)),
        List.of(
            List.of("Nombre: ________________________", "Nombre: ________________________"),
            List.of("Firma: __________________________", "Firma: __________________________"),
            List.of("Fecha: __________________________", "Fecha: __________________________")));
    content.text(
        "Identificación del documento",
        plan.getAcceptedAt() == null
            ? "Proforma para evaluación del paciente. Su emisión no acepta el tratamiento ni"
                + " acredita un pago."
            : "Copia del acuerdo registrado. Las constancias de pago y los consentimientos clínicos"
                + " se conservan como documentos independientes.");
    var data =
        renderer.render(
            profile,
            logos.findById((short) 1).map(l -> l.getContent()).orElse(null),
            plan.getAcceptedAt() == null ? "Proforma de tratamiento" : "Acuerdo de tratamiento",
            plan.getCode() + " · v" + plan.getVersion(),
            content,
            false);
    var document =
        documents.save(
            id,
            plan.getVersion(),
            plan.getStatus(),
            plan.getCode() + "-v" + plan.getVersion(),
            access.actor().getDisplayName(),
            data,
            clock.instant());
    audit.record(
        "PLAN_PROFORMA_ISSUED",
        "PLAN",
        id,
        "Emitió proforma/acuerdo conservando la versión " + plan.getVersion());
    return document;
  }

  @Transactional(readOnly = true)
  public PageResponse<Document> list(UUID plan, PageQuery query) {
    if (!plans.existsById(plan)) throw ApiException.notFound();
    return documents.list(plan, query);
  }

  @Transactional
  public byte[] download(UUID plan, UUID document) {
    documents.get(plan, document);
    audit.record(
        "PLAN_PROFORMA_DOWNLOADED", "PLAN", plan, "Descargó una copia del documento acordado");
    return documents.content(document);
  }

  private String date(LocalDate date) {
    return date.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"));
  }

  private String money(BigDecimal value, String currency) {
    var format = NumberFormat.getCurrencyInstance(Locale.forLanguageTag("es-PE"));
    format.setCurrency(Currency.getInstance(currency));
    return format.format(value);
  }

  private String state(String status) {
    return switch (status) {
      case "DRAFT" -> "Borrador";
      case "PROPOSED" -> "Propuesto";
      case "ACCEPTED" -> "Aceptado";
      case "IN_PROGRESS" -> "En curso";
      case "FINISHED" -> "Finalizado";
      default -> "Cancelado";
    };
  }
}
