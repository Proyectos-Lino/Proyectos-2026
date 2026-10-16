package com.odontocare.agent.service;

import com.odontocare.agent.dto.SupervisionContracts.Change;
import com.odontocare.agent.model.AgentRun;
import com.odontocare.agent.repository.*;
import com.odontocare.appointments.dto.*;
import com.odontocare.appointments.model.AppointmentStatus;
import com.odontocare.appointments.service.*;
import com.odontocare.audit.service.AuditService;
import com.odontocare.installation.repository.InstallationProfileRepository;
import com.odontocare.shared.web.ApiException;
import java.sql.Timestamp;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AgentChangeService {
  private final AgentChangeRepository changes;
  private final AgentRepository runs;
  private final AgentCatalogRepository catalog;
  private final AgentInboxRepository inbox;
  private final AgentIdentityService identity;
  private final AgentSupervisionService supervision;
  private final AgentSupervisionRepository context;
  private final AppointmentService appointments;
  private final AppointmentQueryService appointmentQueries;
  private final AgentChangeMessageFormatter messages;
  private final AvailabilityService availability;
  private final InstallationProfileRepository profiles;
  private final AgentReplyService replies;
  private final AuditService audit;
  private final Clock clock;

  public AgentChangeService(
      AgentChangeRepository changes,
      AgentRepository runs,
      AgentCatalogRepository catalog,
      AgentInboxRepository inbox,
      AgentIdentityService identity,
      AgentSupervisionService supervision,
      AgentSupervisionRepository context,
      AppointmentService appointments,
      AppointmentQueryService appointmentQueries,
      AgentChangeMessageFormatter messages,
      AvailabilityService availability,
      InstallationProfileRepository profiles,
      AgentReplyService replies,
      AuditService audit,
      Clock clock) {
    this.changes = changes;
    this.runs = runs;
    this.catalog = catalog;
    this.inbox = inbox;
    this.identity = identity;
    this.supervision = supervision;
    this.context = context;
    this.appointments = appointments;
    this.appointmentQueries = appointmentQueries;
    this.messages = messages;
    this.availability = availability;
    this.profiles = profiles;
    this.replies = replies;
    this.audit = audit;
    this.clock = clock;
  }

  @Transactional
  public Object ownAppointments(AgentRun run, String search, int page) {
    var input = inbox.message(run.messageId(), false).orElseThrow();
    var verified =
        context
            .verified(run.conversationId(), input.source(), clock.instant())
            .orElseThrow(
                () ->
                    ApiException.badRequest(
                        "Confirma tu nombre completo y si eres el paciente o su responsable antes"
                            + " de consultar citas."));
    UUID patient = (UUID) verified.get("patient_id");
    if (patient == null) return Map.of("items", List.of(), "page", page, "page_size", 5);
    identity.require(run, patient, verified.get("patient_name").toString());
    String appliedSearch = search.strip();
    if (AgentIdentityService.normalize(appliedSearch)
        .equals(AgentIdentityService.normalize(verified.get("patient_name").toString())))
      appliedSearch = "";
    var zone = ZoneId.of(profiles.findById((short) 1).orElseThrow().getTimeZone());
    var items = new ArrayList<Map<String, Object>>();
    for (var a : changes.appointments(patient, appliedSearch, page)) {
      var item =
          new LinkedHashMap<String, Object>(
              Map.of(
                  "appointment_ref",
                  changes.reference(
                      run.conversationId(),
                      input.source(),
                      patient,
                      (UUID) a.get("id"),
                      clock.instant()),
                  "patient_name",
                  verified.get("patient_name"),
                  "service_name",
                  a.get("service_name"),
                  "dentist_name",
                  a.get("dentist_name"),
                  "dentist_id",
                  a.get("dentist_id"),
                  "local_start",
                  ((Timestamp) a.get("starts_at"))
                      .toInstant()
                      .atZone(zone)
                      .toLocalDateTime()
                      .toString(),
                  "duration_minutes",
                  a.get("duration_minutes"),
                  "status",
                  a.get("status")));
      if (a.get("service_id") != null) item.put("service_id", a.get("service_id"));
      items.add(item);
    }
    return Map.of(
        "items",
        items,
        "page",
        page,
        "page_size",
        5,
        "time_zone",
        zone.getId(),
        "search_applied",
        appliedSearch);
  }

  @Transactional
  public Map<String, Object> target(AgentRun run, UUID reference) {
    var input = inbox.message(run.messageId(), false).orElseThrow();
    var ref =
        changes
            .reference(reference, run.conversationId(), input.source(), clock.instant())
            .orElseThrow(ApiException::forbidden);
    var a = changes.appointment((UUID) ref.get("appointment_id"), false);
    var binding =
        context
            .verified(run.conversationId(), input.source(), clock.instant())
            .orElseThrow(ApiException::forbidden);
    identity.require(run, (UUID) a.get("patient_id"), binding.get("patient_name").toString());
    requireChangeable(a, "RESCHEDULE");
    return a;
  }

  @Transactional
  public Change propose(AgentRun run, String action, UUID reference, UUID slotId, String reason) {
    supervision.requireAction(run);
    var input = inbox.message(run.messageId(), false).orElseThrow();
    if (!Objects.equals(runs.latestInbound(run.conversationId(), run.messageId()), run.messageId()))
      throw ApiException.conflict("Llegó otra instrucción; procesa el último mensaje.");
    String normalized = AgentIdentityService.normalize(input.body());
    if (AgentConsent.forbidsProposal(input.body()))
      throw ApiException.badRequest(
          "No se prepara un cambio cuando el paciente lo niega o solo consulta.");
    if (action.equals("CANCEL")
        ? !normalized.matches("(?s).*(cancel|anul).*")
        : !normalized.matches("(?s).*(reprogram|cambiar|mover|otra hora|otro dia).*"))
      throw ApiException.badRequest(
          "El paciente debe solicitar expresamente el cambio o la cancelación.");
    var ref =
        changes
            .reference(reference, run.conversationId(), input.source(), clock.instant())
            .orElseThrow(ApiException::forbidden);
    var appointment = changes.appointment((UUID) ref.get("appointment_id"), true);
    var binding =
        context
            .verified(run.conversationId(), input.source(), clock.instant())
            .orElseThrow(ApiException::forbidden);
    identity.require(
        run, (UUID) appointment.get("patient_id"), binding.get("patient_name").toString());
    requireChangeable(appointment, action);
    var zone = ZoneId.of(profiles.findById((short) 1).orElseThrow().getTimeZone());
    String summary =
        (action.equals("CANCEL") ? "Cancelar" : "Reprogramar")
            + " cita de "
            + binding.get("patient_name")
            + ". Servicio: "
            + appointment.get("service_name")
            + ". Odontólogo: "
            + appointment.get("dentist_name")
            + ". Fecha actual: "
            + format(
                ((Timestamp) appointment.get("starts_at"))
                    .toInstant()
                    .atZone(zone)
                    .toLocalDateTime())
            + " ("
            + zone
            + ").";
    if (action.equals("RESCHEDULE")) {
      var slot = runs.slot(slotId).orElseThrow(ApiException::forbidden);
      var requestedDate =
          AgentRequestedDate.resolve(input.body(), input.createdAt().atZone(zone).toLocalDate());
      if (requestedDate.isPresent() && !requestedDate.get().equals(slot.localStart().toLocalDate()))
        throw ApiException.badRequest(
            "El nuevo horario no coincide con el día solicitado; consulta la fecha correcta.");
      if (AgentRequestedTime.resolve(input.body())
          .filter(time -> !time.equals(slot.localStart().toLocalTime()))
          .isPresent())
        throw ApiException.badRequest(
            "El nuevo horario no coincide con la hora elegida; consulta esa hora antes de"
                + " proponer.");
      if (!slot.conversationId().equals(run.conversationId())
          || !slot.expiresAt().isAfter(clock.instant())
          || !slot.serviceId().equals(appointment.get("service_id"))
          || slot.durationMinutes() != ((Number) appointment.get("duration_minutes")).intValue()
          || !zone.getId().equals(slot.timeZone())
          || !input
              .source()
              .equals(
                  inbox
                      .message(runs.get(slot.runId(), false).orElseThrow().messageId(), false)
                      .orElseThrow()
                      .source()))
        throw ApiException.badRequest(
            "Consulta un horario para reprogramar esta cita conservando su duración.");
      availability.requireAvailable(
          slot.dentistId(),
          slot.localStart().atZone(zone).toInstant(),
          slot.durationMinutes(),
          profiles.findById((short) 1).orElseThrow(),
          (UUID) appointment.get("id"));
      summary +=
          " Nuevo horario: "
              + format(slot.localStart())
              + ". Nuevo odontólogo: "
              + slot.dentistName()
              + ". Duración: "
              + slot.durationMinutes()
              + " minutos.";
    }
    if (reason.isBlank() || reason.length() > 500)
      throw ApiException.badRequest("Indica el motivo del cambio, máximo 500 caracteres.");
    summary += " Motivo: " + reason.strip() + ".";
    var p =
        changes.propose(
            run.id(),
            run.conversationId(),
            input.source(),
            action,
            (UUID) appointment.get("id"),
            (UUID) appointment.get("patient_id"),
            ((Number) appointment.get("version")).longValue(),
            slotId,
            reason.strip(),
            summary,
            clock.instant());
    supervision.request(
        run,
        "CONFIRMATION_PENDING",
        summary,
        p.patientId(),
        binding.get("patient_name").toString(),
        p.appointmentId());
    audit.recordAs(
        null,
        "Agente IA",
        "AGENT_CHANGE_PROPOSED",
        "AGENT_CHANGE",
        p.id(),
        "Preparó cambio de cita pendiente de confirmación: " + action);
    return p;
  }

  private void requireChangeable(Map<String, Object> a, String action) {
    var p = supervision.policy();
    if (action.equals("CANCEL") ? !p.allowCancel() : !p.allowReschedule())
      throw ApiException.conflict(
          "Esta gestión requiere recepción según las reglas del consultorio.");
    if (!Set.of("RESERVED", "CONFIRMED").contains(a.get("status"))
        || !((Timestamp) a.get("starts_at"))
            .toInstant()
            .isAfter(clock.instant().plusSeconds(p.changeLeadMinutes() * 60L)))
      throw ApiException.conflict(
          "La cita no admite cambios automáticos por su estado o anticipación. Contacta a"
              + " recepción.");
  }

  @Transactional(timeout = 15)
  public Map<String, Object> confirm(AgentRun run, String code) {
    supervision.requireAction(run);
    var input = inbox.message(run.messageId(), false).orElseThrow();
    if (!(AgentConfirmation.natural(input.body())
        || AgentConfirmation.code(input.body()).filter(code::equals).isPresent()))
      throw ApiException.forbidden();
    var p = changes.byCode(run.conversationId(), code).orElseThrow(ApiException::forbidden);
    if (!AgentConsent.matchesAction(input.body(), p.action()))
      throw ApiException.badRequest(
          "La confirmación corresponde a otra operación; revisa el resumen del cambio.");
    if (!p.source().equals(input.source())
        || !runs.naturalConfirmationAllowed(p.runId(), input.id())) throw ApiException.forbidden();
    if (!Objects.equals(runs.latestInbound(run.conversationId(), run.messageId()), input.id()))
      throw ApiException.conflict("Llegó una instrucción posterior; no se aplica este cambio.");
    if (p.state().equals("CONFIRMED")) {
      var verified =
          context
              .verified(run.conversationId(), p.source(), clock.instant())
              .orElseThrow(ApiException::forbidden);
      identity.require(run, p.patientId(), verified.get("patient_name").toString());
      var current = appointmentQueries.get(p.appointmentId());
      if (!current.patientId().equals(p.patientId())) throw ApiException.forbidden();
      return Map.of(
          "response",
          messages.repeated(p.action(), current),
          "appointment_id",
          p.appointmentId(),
          "repeated",
          true);
    }
    if (p.state().equals("EXPIRED"))
      return Map.of(
          "response",
          "La propuesta de cambio venció. La cita original se conserva; solicita otra propuesta.");
    if (p.state().equals("SUPERSEDED"))
      return Map.of(
          "response",
          "Esa propuesta de cambio fue descartada. La cita original se conserva; solicita una nueva"
              + " propuesta si deseas cambiarla.");
    if (!p.state().equals("PENDING"))
      throw ApiException.conflict("La propuesta de cambio ya no está vigente; solicita otra.");
    if (!p.expiresAt().isAfter(clock.instant())) {
      changes.state(p.id(), "EXPIRED");
      supervision.request(run, "EXPIRED", p.summary(), null, "", p.appointmentId());
      return Map.of(
          "response",
          "La propuesta de cambio venció. La cita original se conserva. Solicita una nueva"
              + " propuesta.");
    }
    var binding =
        context
            .verified(run.conversationId(), p.source(), clock.instant())
            .orElseThrow(ApiException::forbidden);
    identity.require(run, p.patientId(), binding.get("patient_name").toString());
    var a = changes.appointment(p.appointmentId(), true);
    requireChangeable(a, p.action());
    if (((Number) a.get("version")).longValue() != p.appointmentVersion())
      throw ApiException.conflict(
          "La cita cambió después del resumen; revisa una propuesta actualizada.");
    AppointmentResponse result;
    if (p.action().equals("CANCEL"))
      result =
          appointments.changeStatusByAgent(
              p.appointmentId(),
              new StatusRequest(p.appointmentVersion(), AppointmentStatus.CANCELLED, p.reason()));
    else {
      var slot = runs.slot(p.slotId()).orElseThrow();
      var profile = profiles.findById((short) 1).orElseThrow();
      if (!profile.getTimeZone().equals(slot.timeZone()))
        throw ApiException.conflict(
            "Cambió la zona horaria; solicita un resumen actualizado. La cita original se"
                + " conserva.");
      if (!slot.expiresAt().isAfter(clock.instant()))
        throw ApiException.conflict("El horario ofrecido venció. La cita original se conserva.");
      var service =
          catalog
              .service(slot.serviceId())
              .orElseThrow(
                  () ->
                      ApiException.conflict(
                          "El servicio requiere revisión de recepción. La cita original se"
                              + " conserva."));
      var dentists = catalog.dentists(slot.serviceId(), slot.dentistId());
      if (dentists.isEmpty()
          || !slot.dentistName().equals(dentists.getFirst().get("full_name"))
          || !slot.serviceName().equals(service.get("name")))
        throw ApiException.conflict(
            "Cambió el servicio o profesional; solicita una propuesta actualizada. La cita original"
                + " se conserva.");
      result =
          appointments.rescheduleByAgent(
              p.appointmentId(),
              new RescheduleRequest(
                  p.appointmentVersion(), slot.dentistId(), slot.localStart(), false, p.reason()));
    }
    changes.confirm(p.id(), input.id());
    String text = messages.confirmed(p.action(), result);
    Map<String, Object> response =
        Map.of("response", text, "appointment_id", result.id(), "status", result.status());
    runs.step(
        run.id(),
        "BOOKING",
        p.action().equals("CANCEL") ? "cancelar_cita_confirmada" : "reprogramar_cita_confirmada",
        Map.of("proposal_id", p.id()),
        response,
        "OK",
        clock.instant());
    supervision.request(
        run,
        "COMPLETED",
        p.summary(),
        p.patientId(),
        binding.get("patient_name").toString(),
        result.id());
    replies.complete(run.id(), text);
    context.outcome(run.id(), p.action().equals("CANCEL") ? "CANCELLED" : "RESCHEDULED");
    audit.recordAs(
        null,
        "Agente IA",
        "AGENT_CHANGE_CONFIRMED",
        "AGENT_CHANGE",
        p.id(),
        "Registró cambio de cita confirmado: " + p.action());
    return response;
  }

  public Optional<Change> current(AgentRun run) {
    return changes.current(
        run.conversationId(), inbox.message(run.messageId(), false).orElseThrow().source());
  }

  public Optional<Change> byRun(UUID run) {
    return changes.byRun(run);
  }

  public boolean hasCode(AgentRun run, String code) {
    return changes.existsCode(run.conversationId(), code);
  }

  @Transactional
  public String conflict(AgentRun run, String detail) {
    supervision.requireAutomatic(run);
    var p = current(run).orElseThrow();
    var code = AgentConfirmation.code(inbox.message(run.messageId(), false).orElseThrow().body());
    if (!p.state().equals("PENDING")
        || code.filter(c -> !c.equals(p.confirmationCode())).isPresent())
      throw ApiException.conflict("La propuesta de cambio ya no está vigente; solicita otra.");
    changes.state(p.id(), "CONFLICT");
    supervision.request(
        run, "INFORMATION_PENDING", p.summary(), p.patientId(), "", p.appointmentId());
    return "No se aplicó el cambio; la cita original se conserva. "
        + detail
        + " Podemos consultar otro horario o pedir ayuda a recepción.";
  }

  @Transactional
  public void discard(AgentRun run) {
    changes.discard(
        run.conversationId(), inbox.message(run.messageId(), false).orElseThrow().source());
  }

  public String prepared(Change p) {
    return p.summary()
        + "\n\n¿Confirmas este cambio? Responde «Sí, confirmo» o CONFIRMO "
        + p.confirmationCode()
        + ". La propuesta vence 30 minutos después de su creación; mientras no confirmes, tu cita"
        + " se conserva.";
  }

  private String format(LocalDateTime date) {
    return date.format(
        DateTimeFormatter.ofPattern("EEEE dd/MM/yyyy HH:mm", Locale.forLanguageTag("es")));
  }
}
