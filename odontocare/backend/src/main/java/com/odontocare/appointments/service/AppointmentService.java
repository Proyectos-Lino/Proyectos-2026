package com.odontocare.appointments.service;

import com.odontocare.appointments.dto.*;
import com.odontocare.appointments.model.*;
import com.odontocare.appointments.repository.*;
import com.odontocare.audit.service.AuditService;
import com.odontocare.catalog.repository.DentalServiceRepository;
import com.odontocare.dentists.model.Dentist;
import com.odontocare.dentists.repository.DentistRepository;
import com.odontocare.installation.repository.InstallationProfileRepository;
import com.odontocare.patients.repository.PatientRepository;
import com.odontocare.shared.web.ApiException;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

@Service
public class AppointmentService {
  private final AppointmentRepository appointments;
  private final PatientRepository patients;
  private final DentistRepository dentists;
  private final DentalServiceRepository services;
  private final InstallationProfileRepository profiles;
  private final AvailabilityService availability;
  private final BookingRules rules;
  private final AppointmentStateRules states;
  private final AppointmentHistoryService history;
  private final AuditService audit;
  private final Clock clock;
  private final ObjectMapper mapper;
  private final com.odontocare.treatments.repository.TreatmentPlanningRepository planning;
  private final com.odontocare.treatments.repository.PlanItemRepository planItems;
  private final com.odontocare.treatments.repository.TreatmentPlanRepository plans;
  private final com.odontocare.treatments.repository.PlanSessionRepository planProgress;

  public AppointmentService(
      AppointmentRepository appointments,
      PatientRepository patients,
      DentistRepository dentists,
      DentalServiceRepository services,
      InstallationProfileRepository profiles,
      AvailabilityService availability,
      BookingRules rules,
      AppointmentStateRules states,
      AppointmentHistoryService history,
      AuditService audit,
      Clock clock,
      ObjectMapper mapper,
      com.odontocare.treatments.repository.TreatmentPlanningRepository planning,
      com.odontocare.treatments.repository.PlanItemRepository planItems,
      com.odontocare.treatments.repository.TreatmentPlanRepository plans,
      com.odontocare.treatments.repository.PlanSessionRepository planProgress) {
    this.appointments = appointments;
    this.patients = patients;
    this.dentists = dentists;
    this.services = services;
    this.profiles = profiles;
    this.availability = availability;
    this.rules = rules;
    this.states = states;
    this.history = history;
    this.audit = audit;
    this.clock = clock;
    this.mapper = mapper;
    this.planning = planning;
    this.planItems = planItems;
    this.plans = plans;
    this.planProgress = planProgress;
  }

  @Transactional
  public AppointmentResponse create(AppointmentRequest request) {
    return createInternal(request, "MANUAL");
  }

  /** Internal agent entry point; HTTP callers cannot choose origin or bypass confirmation. */
  @Transactional
  public AppointmentResponse createByAgent(AppointmentRequest request, boolean applicationTest) {
    if (request.serviceId() == null)
      throw ApiException.badRequest("El agente necesita un servicio del catálogo.");
    if (request.planItemId() != null
        || request.scheduledSessionId() != null
        || request.administrative() != null
        || request.agreedPrice() != null)
      throw ApiException.badRequest("La reserva automática solo usa el catálogo habilitado.");
    return createInternal(request, applicationTest ? "AI_TEST" : "WHATSAPP");
  }

  private AppointmentResponse createInternal(AppointmentRequest request, String origin) {
    var profile = profiles.readLockedInstallation().orElseThrow();
    var zone = ZoneId.of(profile.getTimeZone());
    String fingerprint = fingerprint(request, origin.equals("MANUAL") ? "" : origin);
    var repeated = appointments.findByRequestKey(request.requestKey());
    if (repeated.isPresent()) {
      if (!repeated.get().getRequestFingerprint().equals(fingerprint))
        throw ApiException.conflict(
            "La clave de solicitud ya se utilizó para una reserva diferente.");
      return AppointmentResponse.of(repeated.get(), zone);
    }
    com.odontocare.treatments.dto.ScheduledSessionContracts.Session planned = null;
    if (request.planItemId() != null || request.scheduledSessionId() != null) {
      patients.lockById(request.patientId()).orElseThrow(ApiException::notFound);
      UUID itemId = request.planItemId();
      if (request.scheduledSessionId() != null) {
        var found =
            planning
                .session(request.scheduledSessionId(), false)
                .orElseThrow(ApiException::notFound);
        if (itemId != null && !itemId.equals(found.itemId()))
          throw ApiException.badRequest("La sesión no pertenece al tratamiento seleccionado.");
        itemId = found.itemId();
      }
      var item = planItems.findById(itemId).orElseThrow(ApiException::notFound);
      var plan = plans.lockById(item.getPlanId()).orElseThrow();
      if (!Set.of("ACCEPTED", "IN_PROGRESS").contains(plan.getStatus())
          || !plan.getPatientId().equals(request.patientId())
          || !plan.getDentistId().equals(request.dentistId()))
        throw ApiException.badRequest(
            "Selecciona una sesión de un plan activo de este paciente y profesional.");
      planning.ensureRemaining(item, planProgress.completed(item.getId()));
      planned =
          request.scheduledSessionId() == null
              ? planning
                  .firstPending(itemId)
                  .orElseThrow(
                      () ->
                          ApiException.conflict(
                              "Todas las sesiones pendientes ya tienen cita; revisa el plan."))
              : planning.session(request.scheduledSessionId(), true).orElseThrow();
      if (!planned.status().equals("PENDING"))
        throw ApiException.conflict("Esta sesión ya tiene cita o está cerrada.");
      if (request.scheduledSessionVersion() != null
          && planned.version() != request.scheduledSessionVersion())
        throw ApiException.conflict(
            "La planificación cambió; actualiza la sesión antes de asignar hora.");
      if (!Objects.equals(item.getServiceId(), request.serviceId())
          || Boolean.TRUE.equals(request.administrative()))
        throw ApiException.badRequest("El servicio debe coincidir con la sesión del tratamiento.");
    }
    var dentist = dentists.lockById(request.dentistId()).orElseThrow(ApiException::notFound);
    if (Boolean.TRUE.equals(request.administrative())
        && (request.serviceId() != null || planned != null || request.agreedPrice() != null))
      throw ApiException.badRequest(
          "Una cita administrativa no tiene servicio, tratamiento ni precio acordado.");
    var patient = patients.findById(request.patientId()).orElseThrow(ApiException::notFound);
    if (!patient.getActive()) throw ApiException.badRequest("El paciente está inactivo.");
    var service =
        request.serviceId() == null
            ? null
            : services.findById(request.serviceId()).orElseThrow(ApiException::notFound);
    rules.eligible(dentist, service);
    if (!origin.equals("MANUAL") && !service.getBookableByAgent())
      throw ApiException.badRequest("El servicio no está habilitado para reserva automática.");
    int duration =
        planned != null
            ? planned.durationMinutes()
            : service == null
                ? (request.durationMinutes() == null ? 0 : request.durationMinutes())
                : service.getDurationMinutes();
    if (duration < 1 || duration > 1440 || service == null && request.reason().isBlank())
      throw ApiException.badRequest("Una cita administrativa necesita motivo y duración positiva.");
    Instant start = rules.instant(request.localStart(), zone);
    availability.requireAvailable(dentist.getId(), start, duration, profile, null);
    var appointment = new Appointment();
    appointment.setPatient(patient);
    appointment.setDentist(dentist);
    appointment.setDentistName(dentist.getFullName());
    appointment.setService(service);
    appointment.setServiceName(service == null ? request.reason().strip() : service.getName());
    appointment.setAdministrative(
        request.administrative() == null
            ? service == null && planned == null
            : request.administrative());
    appointment.setAgreedPrice(request.agreedPrice());
    if (planned != null) {
      appointment.setPlanItemId(planned.itemId());
      appointment.setScheduledSessionId(planned.id());
      appointment.setServiceName(planned.description());
      appointment.setTreatmentLabel(planned.planCode() + " · " + planned.planTitle());
      appointment.setTreatmentSessionNumber(planned.sessionNumber());
    }
    appointment.setDurationMinutes(duration);
    appointment.setGapMinutes(profile.getAppointmentGapMinutes());
    setInterval(appointment, start);
    appointment.setNotes(request.notes().strip());
    appointment.setRequestKey(request.requestKey());
    appointment.setRequestFingerprint(fingerprint);
    appointment.setOrigin(origin);
    if (!origin.equals("MANUAL")) appointment.setStatus(AppointmentStatus.CONFIRMED);
    appointments.saveAndFlush(appointment);
    if (planned != null) {
      planning.assigned(planned.id(), appointment.getId());
      planning.date(planned.id(), request.localStart().toLocalDate());
    }
    if (origin.equals("MANUAL")) {
      history.append(appointment, "CREATED", null, null, "Reserva manual");
      audit.record(
          "APPOINTMENT_CREATED",
          "APPOINTMENT",
          appointment.getId(),
          "Reservó una cita manual para ficha " + patient.getCode());
    } else {
      history.appendAs(
          appointment,
          "CREATED",
          null,
          null,
          "Confirmación explícita de propuesta del agente",
          "Agente IA");
      audit.recordAs(
          null,
          "Agente IA",
          "APPOINTMENT_AGENT_CREATED",
          "APPOINTMENT",
          appointment.getId(),
          "Registró cita confirmada por propuesta; origen " + origin);
    }
    return AppointmentResponse.of(appointment, zone);
  }

  @Transactional
  public AppointmentResponse reschedule(UUID id, RescheduleRequest request) {
    return rescheduleInternal(id, request, false);
  }

  @Transactional
  public AppointmentResponse rescheduleByAgent(UUID id, RescheduleRequest request) {
    return rescheduleInternal(id, request, true);
  }

  private AppointmentResponse rescheduleInternal(
      UUID id, RescheduleRequest request, boolean agent) {
    var profile = profiles.readLockedInstallation().orElseThrow();
    var appointment = appointments.lockById(id).orElseThrow(ApiException::notFound);
    appointment.checkVersion(request.version());
    states.requireReschedulable(appointment);
    if (appointment.getPlanItemId() != null
        && !appointment.getDentist().getId().equals(request.dentistId()))
      throw ApiException.badRequest("La sesión conserva el profesional de su plan de tratamiento.");
    Map<UUID, Dentist> locked = new HashMap<>();
    java.util.stream.Stream.of(appointment.getDentist().getId(), request.dentistId())
        .distinct()
        .sorted()
        .forEach(
            key -> locked.put(key, dentists.lockById(key).orElseThrow(ApiException::notFound)));
    var dentist = locked.get(request.dentistId());
    rules.eligible(dentist, appointment.getService());
    if (agent
        && (appointment.getService() == null || !appointment.getService().getBookableByAgent()))
      throw ApiException.conflict("El servicio requiere reprogramación por recepción.");
    if (!appointment.getPatient().getActive())
      throw ApiException.badRequest("El paciente está inactivo.");
    int duration =
        request.useCurrentDuration() && appointment.getService() != null
            ? appointment.getService().getDurationMinutes()
            : appointment.getDurationMinutes();
    ZoneId zone = ZoneId.of(profile.getTimeZone());
    Instant start = rules.instant(request.localStart(), zone);
    availability.requireAvailable(dentist.getId(), start, duration, profile, id);
    var previousStart = appointment.getStartsAt();
    var previousStatus = appointment.getStatus();
    appointment.setDentist(dentist);
    appointment.setDentistName(dentist.getFullName());
    appointment.setDurationMinutes(duration);
    appointment.setGapMinutes(profile.getAppointmentGapMinutes());
    setInterval(appointment, start);
    appointment.setStatus(agent ? AppointmentStatus.CONFIRMED : AppointmentStatus.RESERVED);
    appointments.saveAndFlush(appointment);
    planning.appointmentState(
        appointment.getId(), appointment.getStatus().name(), request.localStart().toLocalDate());
    if (agent)
      history.appendAs(
          appointment,
          "RESCHEDULED",
          previousStatus,
          previousStart,
          request.reason().strip(),
          "Agente IA");
    else
      history.append(
          appointment, "RESCHEDULED", previousStatus, previousStart, request.reason().strip());
    if (agent)
      audit.recordAs(
          null,
          "Agente IA",
          "APPOINTMENT_RESCHEDULED",
          "APPOINTMENT",
          id,
          "Reprogramó cita tras la confirmación expresa del paciente");
    else
      audit.record(
          "APPOINTMENT_RESCHEDULED",
          "APPOINTMENT",
          id,
          "Reprogramó cita y solicitó una nueva confirmación");
    return AppointmentResponse.of(appointment, zone);
  }

  @Transactional
  public AppointmentResponse changeStatus(UUID id, StatusRequest request) {
    return changeStatusInternal(id, request, false);
  }

  @Transactional
  public AppointmentResponse changeStatusByAgent(UUID id, StatusRequest request) {
    if (request.status() != AppointmentStatus.CANCELLED) throw ApiException.forbidden();
    return changeStatusInternal(id, request, true);
  }

  private AppointmentResponse changeStatusInternal(UUID id, StatusRequest request, boolean agent) {
    var profile = profiles.readLockedInstallation().orElseThrow();
    var appointment = appointments.lockById(id).orElseThrow(ApiException::notFound);
    appointment.checkVersion(request.version());
    dentists.lockById(appointment.getDentist().getId()).orElseThrow(ApiException::notFound);
    states.requireTransition(appointment, request.status(), request.reason(), clock);
    var previous = appointment.getStatus();
    appointment.setStatus(request.status());
    appointments.saveAndFlush(appointment);
    planning.appointmentState(
        appointment.getId(),
        request.status().name(),
        LocalDate.ofInstant(appointment.getStartsAt(), ZoneId.of(profile.getTimeZone())));
    if (agent)
      history.appendAs(
          appointment,
          "CANCELLED",
          previous,
          appointment.getStartsAt(),
          request.reason().strip(),
          "Agente IA");
    else
      history.append(
          appointment,
          request.status() == AppointmentStatus.CANCELLED ? "CANCELLED" : "STATUS_CHANGED",
          previous,
          appointment.getStartsAt(),
          request.reason().strip());
    if (agent)
      audit.recordAs(
          null,
          "Agente IA",
          "APPOINTMENT_STATUS",
          "APPOINTMENT",
          id,
          "Canceló cita tras la confirmación expresa del paciente");
    else
      audit.record(
          "APPOINTMENT_STATUS",
          "APPOINTMENT",
          id,
          "Cambió estado de " + previous + " a " + request.status());
    return AppointmentResponse.of(appointment, ZoneId.of(profile.getTimeZone()));
  }

  @Transactional
  public void completeClinical(UUID id) {
    profiles.readLockedInstallation().orElseThrow();
    var appointment = appointments.lockById(id).orElseThrow(ApiException::notFound);
    if (appointment.getStatus() == AppointmentStatus.ATTENDED) return;
    if (Set.of(AppointmentStatus.CANCELLED, AppointmentStatus.NO_SHOW)
        .contains(appointment.getStatus()))
      throw ApiException.conflict("La cita ya no admite una atención clínica.");
    if (appointment.getStartsAt().isAfter(clock.instant()))
      throw ApiException.badRequest(
          "La atención vinculada solo se finaliza al llegar la hora de la cita.");
    for (var next :
        List.of(
            AppointmentStatus.WAITING, AppointmentStatus.IN_PROGRESS, AppointmentStatus.ATTENDED)) {
      if (appointment.getStatus() == next) continue;
      if (next == AppointmentStatus.WAITING
          && appointment.getStatus() == AppointmentStatus.IN_PROGRESS) continue;
      changeStatus(
          id,
          new StatusRequest(appointment.getVersion(), next, "Finalización de atención clínica"));
    }
  }

  private void setInterval(Appointment appointment, Instant start) {
    appointment.setStartsAt(start);
    appointment.setEndsAt(start.plusSeconds(appointment.getDurationMinutes() * 60L));
    appointment.setBlockedUntil(
        appointment.getEndsAt().plusSeconds(appointment.getGapMinutes() * 60L));
  }

  private String fingerprint(AppointmentRequest request, String origin) {
    try {
      return HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256")
                  .digest(
                      (origin
                              + mapper.writeValueAsString(
                                  request.administrative() == null
                                          && request.planItemId() == null
                                          && request.scheduledSessionId() == null
                                          && request.agreedPrice() == null
                                      ? new LegacyRequest(
                                          request.patientId(),
                                          request.dentistId(),
                                          request.serviceId(),
                                          request.reason(),
                                          request.durationMinutes(),
                                          request.localStart(),
                                          request.notes(),
                                          request.requestKey())
                                      : request))
                          .getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }

  private record LegacyRequest(
      UUID patientId,
      UUID dentistId,
      UUID serviceId,
      String reason,
      Integer durationMinutes,
      LocalDateTime localStart,
      String notes,
      UUID requestKey) {}
}
