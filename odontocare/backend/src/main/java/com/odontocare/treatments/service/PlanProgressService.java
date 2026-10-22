package com.odontocare.treatments.service;

import com.odontocare.audit.service.AuditService;
import com.odontocare.clinical.dto.ClinicalContracts.Procedure;
import com.odontocare.clinical.model.Encounter;
import com.odontocare.clinical.service.ClinicalAccess;
import com.odontocare.shared.web.ApiException;
import com.odontocare.treatments.model.*;
import com.odontocare.treatments.repository.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PlanProgressService {
  private final TreatmentPlanRepository plans;
  private final PlanItemRepository items;
  private final PlanSessionRepository sessions;
  private final ClinicalAccess access;
  private final AuditService audit;
  private final TreatmentPlanningRepository planning;

  public PlanProgressService(
      TreatmentPlanRepository plans,
      PlanItemRepository items,
      PlanSessionRepository sessions,
      ClinicalAccess access,
      AuditService audit,
      TreatmentPlanningRepository planning) {
    this.plans = plans;
    this.items = items;
    this.sessions = sessions;
    this.access = access;
    this.audit = audit;
    this.planning = planning;
  }

  public PlanItem validate(Encounter encounter, Procedure procedure) {
    var item = items.findById(procedure.planItemId()).orElseThrow(ApiException::notFound);
    var plan = plans.lockById(item.getPlanId()).orElseThrow();
    if (!Set.of("ACCEPTED", "IN_PROGRESS").contains(plan.getStatus()))
      throw ApiException.conflict("El tratamiento requiere un plan aceptado y vigente.");
    if (!plan.getPatientId().equals(encounter.getPatientId())
        || !plan.getDentistId().equals(encounter.getDentistId()))
      throw ApiException.badRequest("El plan pertenece a otro paciente o profesional.");
    if (!Objects.equals(item.getServiceId(), procedure.serviceId())
        || !Objects.equals(item.getTooth(), procedure.tooth()))
      throw ApiException.badRequest(
          "Servicio y pieza deben coincidir con el tratamiento del plan.");
    return item;
  }

  @Transactional
  public void complete(Encounter encounter, Procedure procedure, int index) {
    var item = validate(encounter, procedure);
    if (sessions.completed(item.getId()) + procedure.quantity() > item.getSessions())
      throw ApiException.conflict("La atención supera las sesiones pendientes del tratamiento.");
    planning.complete(item.getId(), encounter.getAppointmentId(), procedure.quantity());
    var session = new PlanSession();
    session.setPlanId(item.getPlanId());
    session.setItemId(item.getId());
    session.setEncounterId(encounter.getId());
    session.setProcedureIndex(index);
    session.setSessions(procedure.quantity());
    session.setActorName(access.actor().getDisplayName());
    sessions.saveAndFlush(session);
    var plan = plans.lockById(item.getPlanId()).orElseThrow();
    if (plan.getStatus().equals("ACCEPTED")) {
      plan.setStatus("IN_PROGRESS");
    }
    plan.advanceRevision();
    plans.saveAndFlush(plan);
    audit.record(
        "PLAN_SESSION_COMPLETED",
        "PLAN",
        plan.getId(),
        "Registró avance desde atención clínica sin generar deuda adicional");
  }
}
