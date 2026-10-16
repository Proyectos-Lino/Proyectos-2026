package com.odontocare.agent.service;

import com.odontocare.agent.dto.AgentContracts.*;
import com.odontocare.agent.model.AgentRun;
import com.odontocare.agent.repository.*;
import com.odontocare.appointments.dto.AppointmentRequest;
import com.odontocare.appointments.service.AppointmentService;
import com.odontocare.audit.service.AuditService;
import com.odontocare.installation.repository.InstallationProfileRepository;
import com.odontocare.patients.dto.PatientRequest;
import com.odontocare.patients.service.PatientService;
import com.odontocare.shared.web.ApiException;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AgentBookingService {
  private final AgentRepository runs;
  private final AgentCatalogRepository catalog;
  private final AgentInboxRepository messages;
  private final AppointmentService appointments;
  private final PatientService patients;
  private final InstallationProfileRepository profiles;
  private final AuditService audit;
  private final Clock clock;
  private final AgentSupervisionService supervision;
  private final AgentReplyService replies;
  private final AgentIdentityService identity;

  public AgentBookingService(
      AgentRepository runs,
      AgentCatalogRepository catalog,
      AgentInboxRepository messages,
      AppointmentService appointments,
      PatientService patients,
      InstallationProfileRepository profiles,
      AuditService audit,
      AgentSupervisionService supervision,
      Clock clock,
      AgentReplyService replies,
      AgentIdentityService identity) {
    this.runs = runs;
    this.catalog = catalog;
    this.messages = messages;
    this.appointments = appointments;
    this.patients = patients;
    this.profiles = profiles;
    this.audit = audit;
    this.clock = clock;
    this.supervision = supervision;
    this.replies = replies;
    this.identity = identity;
  }

  @Transactional(timeout = 15)
  public Map<String, Object> confirm(AgentRun run, String code) {
    supervision.requireAction(run);
    var message = messages.message(run.messageId(), false).orElseThrow(ApiException::notFound);
    if (!AgentConsent.matchesAction(message.body(), "BOOK"))
      throw ApiException.badRequest(
          "Esa confirmación corresponde a un cambio; revisa la propuesta de reserva.");
    if (!message.direction().equals("INBOUND")
        || !(message.body().strip().matches("(?i)CONFIRMO\\s+" + code + "[.!]?")
            || AgentConfirmation.natural(message.body()))) throw ApiException.forbidden();
    var conversation =
        messages.conversation(run.conversationId(), true).orElseThrow(ApiException::notFound);
    if (!Objects.equals(runs.latestInbound(run.conversationId(), run.messageId()), message.id()))
      throw ApiException.conflict(
          "Llegó otro mensaje; no se reserva con una confirmación anterior.");
    var p =
        runs.proposalByCode(run.conversationId(), code)
            .orElseThrow(
                () ->
                    ApiException.badRequest(
                        "El código no corresponde a una propuesta de este contacto."));
    var original =
        messages.message(runs.get(p.runId(), false).orElseThrow().messageId(), false).orElseThrow();
    if (!original.source().equals(message.source())) throw ApiException.forbidden();
    if (message.source().equals("KAPSO")
        && !runs.naturalConfirmationAllowed(p.runId(), message.id()))
      throw ApiException.badRequest(
          "Primero revisa la propuesta completa enviada y confirma esos datos.");
    if (AgentConfirmation.code(message.body()).isEmpty()
        && !runs.naturalConfirmationAllowed(p.runId(), message.id()))
      throw ApiException.badRequest(
          "Primero revisa la propuesta completa enviada y confirma esos datos.");
    if (p.state().equals("CONFIRMED"))
      return Map.of(
          "appointment_id",
          p.appointmentId(),
          "response",
          "La cita ya estaba registrada. Referencia: " + p.appointmentId() + ". " + p.summary(),
          "repeated",
          true);
    if (p.state().equals("EXPIRED"))
      return Map.of(
          "response",
          "La propuesta venció. Solicita un nuevo horario; no se creó una cita.",
          "appointment_created",
          false);
    if (!p.state().equals("PENDING"))
      throw ApiException.conflict(
          "Esa propuesta fue descartada o ya no está vigente; solicita otra.");
    if (!p.expiresAt().isAfter(clock.instant())) {
      runs.proposalState(p.id(), "EXPIRED");
      return Map.of(
          "response",
          "La propuesta venció. Envía nuevamente el servicio y horario para consultar"
              + " disponibilidad.",
          "appointment_created",
          false);
    }
    var slot = runs.slot(p.slotId()).orElseThrow();
    var profile = profiles.findById((short) 1).orElseThrow();
    var requestedDate =
        AgentRequestedDate.resolve(
            original.body(),
            original.createdAt().atZone(ZoneId.of(profile.getTimeZone())).toLocalDate());
    if (requestedDate.isPresent() && !requestedDate.get().equals(slot.localStart().toLocalDate()))
      throw ApiException.badRequest(
          "La propuesta no coincide con el día solicitado. No se creó la cita; pide una nueva"
              + " propuesta.");
    var service =
        catalog
            .service(slot.serviceId())
            .orElseThrow(
                () ->
                    ApiException.conflict(
                        "El servicio ya no está habilitado; solicita otra propuesta."));
    if (!profile.getTimeZone().equals(slot.timeZone())
        || ((Number) service.get("duration_minutes")).intValue() != slot.durationMinutes())
      throw ApiException.conflict(
          "Cambió la duración o zona horaria; solicita una nueva propuesta.");
    UUID patient = p.patientId();
    var verified = identity.require(run, patient, p.patientName());
    var dentist = catalog.dentists(slot.serviceId(), slot.dentistId());
    if (dentist.isEmpty()
        || !slot.serviceName().equals(service.get("name"))
        || !slot.dentistName().equals(dentist.getFirst().get("full_name")))
      throw ApiException.conflict(
          "Cambió el servicio o profesional; solicita una propuesta actualizada.");
    if (patient != null) {
      var currentPatient =
          catalog.patient(conversation.phone(), patient).orElseThrow(ApiException::forbidden);
      if (!currentPatient.get("full_name").toString().equals(p.patientName()))
        throw ApiException.conflict(
            "Cambió la ficha del paciente; confirma una propuesta actualizada.");
    } else {
      var existing =
          catalog
              .exactPatients(conversation.phone(), AgentIdentityService.normalize(p.patientName()))
              .stream()
              .toList();
      if (existing.size() > 1)
        throw ApiException.conflict(
            "Hay varias fichas con ese nombre. Selecciona explícitamente una antes de reservar.");
      if (existing.size() == 1) {
        patient = (UUID) existing.getFirst().get("id");
        identity.requireRelationship(
            patient, conversation.phone(), verified.get("relationship").toString());
      } else
        patient =
            patients
                .create(
                    new PatientRequest(
                        null,
                        p.patientName(),
                        null,
                        "",
                        "",
                        "",
                        "",
                        "",
                        "",
                        "Ficha provisional creada tras confirmar propuesta del agente.",
                        true,
                        true,
                        List.of(
                            new PatientRequest.ContactRequest(
                                conversation.phone(),
                                conversation.contactName().isBlank()
                                    ? p.patientName()
                                    : conversation.contactName(),
                                verified.get("relationship").equals("GUARDIAN")
                                    ? "Tutor WhatsApp"
                                    : "Paciente",
                                verified.get("relationship").equals("GUARDIAN"),
                                true))))
                .id();
    }
    var booking =
        appointments.createByAgent(
            new AppointmentRequest(
                patient,
                slot.dentistId(),
                slot.serviceId(),
                "",
                null,
                slot.localStart(),
                "Propuesta del agente " + p.id(),
                p.id()),
            message.source().equals("APP_TEST"));
    runs.confirmed(p.id(), booking.id(), message.id());
    supervision.request(
        run, "COMPLETED", p.summary(), booking.patientId(), p.patientName(), booking.id());
    audit.recordAs(
        null,
        "Agente IA",
        "AGENT_BOOKING_CONFIRMED",
        "AGENT_PROPOSAL",
        p.id(),
        "Guardó cita y confirmación vinculadas.");
    Map<String, Object> result =
        Map.of(
            "appointment_id",
            booking.id(),
            "patient_id",
            booking.patientId(),
            "status",
            booking.status(),
            "origin",
            booking.origin(),
            "response",
            "¡Tu cita quedó confirmada! "
                + p.summary()
                + " Referencia: "
                + booking.id()
                + (message.source().equals("APP_TEST")
                        || messages.provider(run.conversationId()).equals("TWILIO")
                    ? ". Esta respuesta está preparada en el sistema y aún no se envió a WhatsApp."
                    : ". Te esperamos."));
    runs.step(
        run.id(),
        "BOOKING",
        "crear_cita_confirmada",
        Map.of("proposal_id", p.id()),
        result,
        "OK",
        clock.instant());
    replies.complete(run.id(), result.get("response").toString());
    supervision.completed(run, "BOOKED");
    return result;
  }
}
