package com.odontocare.clinical.service;

import com.odontocare.appointments.model.AppointmentStatus;
import com.odontocare.appointments.repository.AppointmentRepository;
import com.odontocare.catalog.repository.DentalServiceRepository;
import com.odontocare.clinical.dto.ClinicalContracts.*;
import com.odontocare.clinical.dto.EncounterWorkflowContracts.*;
import com.odontocare.clinical.repository.EncounterRepository;
import com.odontocare.installation.repository.InstallationProfileRepository;
import com.odontocare.shared.web.ApiException;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EncounterWorkflowService {
  private final AppointmentRepository appointments;
  private final EncounterRepository records;
  private final EncounterService encounters;
  private final ClinicalAccess access;
  private final DentalServiceRepository services;
  private final InstallationProfileRepository profiles;
  private final com.odontocare.treatments.repository.PlanItemRepository planItems;

  public EncounterWorkflowService(
      AppointmentRepository appointments,
      EncounterRepository records,
      EncounterService encounters,
      ClinicalAccess access,
      DentalServiceRepository services,
      InstallationProfileRepository profiles,
      com.odontocare.treatments.repository.PlanItemRepository planItems) {
    this.appointments = appointments;
    this.records = records;
    this.encounters = encounters;
    this.access = access;
    this.services = services;
    this.profiles = profiles;
    this.planItems = planItems;
  }

  @Transactional
  public Start fromAppointment(UUID id) {
    var appointment = appointments.findById(id).orElseThrow(ApiException::notFound);
    var dentist = access.requireDentist(appointment.getDentist().getId());
    var prior = records.findByAppointmentId(id);
    if (prior.isPresent())
      return new Start(encounters.get(prior.get().getId()), null, dentist.getFullName());
    if (Set.of(AppointmentStatus.CANCELLED, AppointmentStatus.NO_SHOW)
        .contains(appointment.getStatus()))
      throw ApiException.badRequest("Esta cita no admite una atención.");
    var service =
        appointment.getService() == null
            ? null
            : services.findById(appointment.getService().getId()).orElseThrow();
    var planItem =
        appointment.getPlanItemId() == null
            ? null
            : planItems.findById(appointment.getPlanItemId()).orElseThrow();
    var procedures =
        appointment.getAdministrative()
            ? List.<Procedure>of()
            : List.of(
                new Procedure(
                    service == null ? null : service.getId(),
                    appointment.getServiceName(),
                    1,
                    planItem == null ? null : planItem.getTooth(),
                    appointment.getPlanItemId(),
                    planItem != null
                        ? null
                        : appointment.getAgreedPrice() != null
                            ? appointment.getAgreedPrice()
                            : service == null ? null : service.getPrice()));
    var date =
        LocalDate.ofInstant(
            appointment.getStartsAt(),
            ZoneId.of(profiles.findById((short) 1).orElseThrow().getTimeZone()));
    if (date.isAfter(access.today())) date = access.today();
    return new Start(
        null,
        new EncounterRequest(
            appointment.getPatient().getId(),
            appointment.getDentist().getId(),
            id,
            date,
            appointment.getServiceName(),
            new EncounterContent("", "", "", "", procedures),
            0L),
        dentist.getFullName());
  }

  @Transactional(readOnly = true)
  public EncounterResponse previous(UUID patient, UUID dentist, UUID exclude, UUID planId) {
    access.requireDentist(dentist);
    var spec =
        com.odontocare.shared.pagination.SearchSpecifications
            .<com.odontocare.clinical.model.Encounter>equal("patientId", patient)
            .and(com.odontocare.shared.pagination.SearchSpecifications.equal("dentistId", dentist))
            .and(com.odontocare.shared.pagination.SearchSpecifications.equal("status", "FINAL"));
    if (exclude != null) spec = spec.and((root, query, cb) -> cb.notEqual(root.get("id"), exclude));
    if (planId != null)
      spec =
          spec.and(
              (root, query, cb) -> {
                var sub = query.subquery(UUID.class);
                var session = sub.from(com.odontocare.treatments.model.PlanSession.class);
                sub.select(session.get("encounterId"))
                    .where(
                        cb.equal(session.get("planId"), planId),
                        cb.equal(session.get("encounterId"), root.get("id")));
                return cb.exists(sub);
              });
    var page =
        records.findAll(
            spec,
            org.springframework.data.domain.PageRequest.of(
                0,
                1,
                org.springframework.data.domain.Sort.by(
                    org.springframework.data.domain.Sort.Direction.DESC,
                    "attendedOn",
                    "createdAt",
                    "id")));
    return page.isEmpty() ? null : encounters.get(page.getContent().getFirst().getId());
  }
}
