package com.odontocare.patients.service;

import com.odontocare.audit.service.AuditService;
import com.odontocare.installation.repository.InstallationProfileRepository;
import com.odontocare.patients.dto.*;
import com.odontocare.patients.model.*;
import com.odontocare.patients.repository.*;
import com.odontocare.shared.pagination.*;
import com.odontocare.shared.web.ApiException;
import java.time.ZoneId;
import java.util.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PatientService {
  private final PatientRepository patients;
  private final InstallationProfileRepository profiles;
  private final PatientValidator validator;
  private final AuditService audit;

  public PatientService(
      PatientRepository patients,
      InstallationProfileRepository profiles,
      PatientValidator validator,
      AuditService audit) {
    this.patients = patients;
    this.profiles = profiles;
    this.validator = validator;
    this.audit = audit;
  }

  @Transactional(readOnly = true)
  public PageResponse<PatientResponse> list(
      PageQuery query, Boolean active, Boolean provisional, String phone) {
    Specification<Patient> spec =
        SearchSpecifications.<Patient>text(query.getSearch(), "fullName", "code", "documentNumber")
            .or(contactSearch(query.getSearch()))
            .and(SearchSpecifications.equal("active", active))
            .and(SearchSpecifications.equal("provisional", provisional));
    if (phone != null && !phone.isBlank())
      spec =
          spec.and(
              (root, criteriaQuery, cb) -> cb.equal(root.join("contacts").get("phone"), phone));
    spec =
        spec.and(
            (root, criteriaQuery, cb) -> {
              criteriaQuery.distinct(true);
              return cb.conjunction();
            });
    return PageResponse.of(
        patients
            .findAll(
                spec,
                query.pageable(
                    Map.of("name", "fullName", "code", "code", "createdAt", "createdAt")))
            .map(PatientResponse::of));
  }

  @Transactional(readOnly = true)
  public PatientResponse get(UUID id) {
    return PatientResponse.of(patients.findById(id).orElseThrow(ApiException::notFound));
  }

  @Transactional
  public PatientResponse create(PatientRequest request) {
    var profile = profiles.lockInstallation().orElseThrow();
    var patient = new Patient();
    int number = profile.getPatientNextNumber();
    String code;
    do {
      code = profile.getPatientPrefix() + "-" + String.format(Locale.ROOT, "%06d", number++);
    } while (patients.existsByCode(code));
    profile.setPatientNextNumber(number);
    patient.setCode(code);
    apply(patient, request, profile.getTimeZone());
    patients.saveAndFlush(patient);
    audit.record("PATIENT_CREATED", "PATIENT", patient.getId(), "Creó ficha " + code);
    return PatientResponse.of(patient);
  }

  @Transactional
  public PatientResponse update(UUID id, PatientRequest request) {
    var profile = profiles.lockInstallation().orElseThrow();
    var patient = patients.findById(id).orElseThrow(ApiException::notFound);
    patient.checkVersion(request.version());
    apply(patient, request, profile.getTimeZone());
    patients.saveAndFlush(patient);
    audit.record("PATIENT_UPDATED", "PATIENT", id, "Actualizó ficha " + patient.getCode());
    return PatientResponse.of(patient);
  }

  private Specification<Patient> contactSearch(String search) {
    return (root, query, cb) -> {
      if (search.isBlank()) return cb.conjunction();
      String pattern =
          "%"
              + search
                  .toLowerCase(Locale.ROOT)
                  .replace("\\", "\\\\")
                  .replace("%", "\\%")
                  .replace("_", "\\_")
              + "%";
      var c = root.join("contacts");
      return cb.or(
          cb.like(cb.lower(c.get("phone")), pattern, '\\'),
          cb.like(cb.lower(c.get("name")), pattern, '\\'));
    };
  }

  private void apply(Patient patient, PatientRequest request, String zone) {
    validator.validate(request, ZoneId.of(zone));
    String number = request.documentNumber().strip().toUpperCase(Locale.ROOT);
    String key = validator.duplicateKey(request);
    UUID except = patient.getId() == null ? new UUID(0, 0) : patient.getId();
    if (!number.isEmpty()
        && patients.existsByDocumentTypeAndDocumentNumberAndIdNot(
            request.documentType(), number, except))
      throw ApiException.conflict("Ya existe un paciente con ese documento. Busca su ficha.");
    String name = request.fullName().strip().replaceAll("\\s+", " ");
    var phones = request.contacts().stream().map(PatientRequest.ContactRequest::phone).toList();
    Specification<Patient> probable =
        (root, query, cb) -> {
          var contact = root.join("contacts");
          return cb.and(
              cb.notEqual(root.get("id"), except),
              cb.equal(cb.lower(root.get("fullName")), name.toLowerCase(Locale.ROOT)),
              request.birthDate() == null
                  ? cb.isNull(root.get("birthDate"))
                  : cb.equal(root.get("birthDate"), request.birthDate()),
              contact.get("phone").in(phones));
        };
    if (patients.exists(probable))
      throw ApiException.conflict(
          "Ya existe una ficha con el mismo nombre, nacimiento y teléfono. Revisa el paciente antes"
              + " de duplicarlo.");
    patient.setFullName(name);
    patient.setBirthDate(request.birthDate());
    patient.setDocumentType(request.documentType());
    patient.setDocumentNumber(number);
    patient.setAddress(request.address().strip());
    patient.setEmail(request.email().strip());
    patient.setEmergencyName(request.emergencyName().strip());
    patient.setEmergencyPhone(request.emergencyPhone());
    patient.setNotes(request.notes().strip());
    patient.setProvisional(request.provisional());
    patient.setActive(request.active());
    patient.setDuplicateKey(key);
    patient.advanceContactRevision();
    patient.getContacts().clear();
    patients.flush();
    request
        .contacts()
        .forEach(
            c ->
                patient
                    .getContacts()
                    .add(
                        new PatientContact(
                            patient,
                            c.phone(),
                            c.name().strip(),
                            c.relationship().strip(),
                            c.guardian(),
                            c.payer())));
  }
}
