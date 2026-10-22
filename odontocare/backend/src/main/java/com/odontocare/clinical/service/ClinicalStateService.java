package com.odontocare.clinical.service;

import com.odontocare.audit.service.AuditService;
import com.odontocare.clinical.dto.ClinicalContracts.*;
import com.odontocare.clinical.model.ClinicalState;
import com.odontocare.clinical.repository.ClinicalStateRepository;
import com.odontocare.patients.repository.PatientRepository;
import com.odontocare.shared.pagination.*;
import com.odontocare.shared.web.ApiException;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

@Service
public class ClinicalStateService {
  private final ClinicalStateRepository states;
  private final PatientRepository patients;
  private final ClinicalAccess access;
  private final AuditService audit;
  private final ObjectMapper mapper;

  public ClinicalStateService(
      ClinicalStateRepository states,
      PatientRepository patients,
      ClinicalAccess access,
      AuditService audit,
      ObjectMapper mapper) {
    this.states = states;
    this.patients = patients;
    this.access = access;
    this.audit = audit;
    this.mapper = mapper;
  }

  @Transactional
  public PageResponse<StateResponse> list(UUID patientId, String kind, PageQuery query) {
    checkKind(kind);
    var page =
        states.findAll(
            SearchSpecifications.<ClinicalState>text(query.getSearch(), "reason", "actorName")
                .and(SearchSpecifications.equal("patientId", patientId))
                .and(SearchSpecifications.equal("kind", kind)),
            query.pageable(
                Map.of(
                    "name",
                    "recordedOn",
                    "recordedOn",
                    "recordedOn",
                    "createdAt",
                    "sequence",
                    "sequence",
                    "sequence")));
    audit.record(
        "CLINICAL_STATE_READ", "CLINICAL_STATE", patientId, "Consultó estados clínicos: " + kind);
    return PageResponse.of(page.map(this::response));
  }

  @Transactional
  public StateResponse create(String kind, StateRequest request) {
    checkKind(kind);
    var dentist = access.requireDentist(request.dentistId());
    access.requireDate(request.recordedOn());
    var patient = patients.lockById(request.patientId()).orElseThrow(ApiException::notFound);
    if (!patient.getActive()) throw ApiException.badRequest("El paciente está inactivo.");
    var previous = states.findFirstByPatientIdAndKindOrderBySequenceDesc(patient.getId(), kind);
    if (!Objects.equals(previous.map(ClinicalState::getId).orElse(null), request.previousId()))
      throw ApiException.conflict(
          "Los datos clínicos cambiaron. Consulta el estado más reciente antes de guardar.");
    if (kind.equals("BACKGROUND") && (request.background() == null || request.odontogram() != null)
        || kind.equals("ODONTOGRAM")
            && (request.odontogram() == null || request.background() != null))
      throw ApiException.badRequest("El contenido no corresponde al tipo de registro.");
    if (request.odontogram() != null) {
      Set<String> keys = new HashSet<>();
      for (var mark : request.odontogram().marks()) {
        ClinicalAccess.requireTooth(mark.tooth());
        if (!keys.add(mark.tooth() + ":" + mark.surface()))
          throw ApiException.badRequest("Una pieza y superficie aparece más de una vez.");
        if (Set.of("MISSING", "EXTRACTION", "CROWN", "ROOT_CANAL").contains(mark.finding())
            && !mark.surface().equals("TOOTH"))
          throw ApiException.badRequest("Ese hallazgo corresponde a la pieza completa.");
      }
    }
    var state = new ClinicalState();
    state.setPatientId(patient.getId());
    state.setSequence(previous.map(item -> item.getSequence() + 1).orElse(1));
    state.setDentistId(dentist.getId());
    state.setDentistName(dentist.getFullName());
    state.setKind(kind);
    state.setRecordedOn(request.recordedOn());
    state.setPreviousId(request.previousId());
    state.setPayload(
        mapper.writeValueAsString(
            kind.equals("BACKGROUND") ? request.background() : request.odontogram()));
    state.setActorName(access.actor().getDisplayName());
    state.setReason(request.reason().strip());
    states.saveAndFlush(state);
    audit.record(
        "CLINICAL_STATE_CREATED", "CLINICAL_STATE", state.getId(), "Registró estado " + kind);
    return response(state);
  }

  private void checkKind(String kind) {
    if (!Set.of("BACKGROUND", "ODONTOGRAM").contains(kind))
      throw ApiException.badRequest("Tipo de estado clínico no permitido.");
  }

  private StateResponse response(ClinicalState state) {
    return new StateResponse(
        state.getId(),
        state.getPatientId(),
        state.getKind(),
        state.getRecordedOn(),
        state.getPreviousId(),
        state.getKind().equals("BACKGROUND")
            ? mapper.readValue(state.getPayload(), Background.class)
            : null,
        state.getKind().equals("ODONTOGRAM")
            ? mapper.readValue(state.getPayload(), Odontogram.class)
            : null,
        state.getActorName(),
        state.getReason(),
        state.getSequence(),
        state.getDentistId(),
        state.getDentistName(),
        state.getCreatedAt());
  }
}
