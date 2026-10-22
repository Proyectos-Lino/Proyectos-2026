package com.odontocare.documents.service;

import com.odontocare.audit.service.AuditService;
import com.odontocare.clinical.repository.EncounterRepository;
import com.odontocare.clinical.service.ClinicalAccess;
import com.odontocare.documents.dto.DocumentContracts.*;
import com.odontocare.documents.model.*;
import com.odontocare.documents.repository.*;
import com.odontocare.patients.repository.PatientRepository;
import com.odontocare.shared.pagination.*;
import com.odontocare.shared.web.ApiException;
import java.security.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class PatientDocumentService {
  private final PatientDocumentRepository documents;
  private final DocumentContentRepository contents;
  private final DocumentCategoryRepository categories;
  private final DocumentPolicyRepository policies;
  private final DocumentConsentRepository consents;
  private final PatientRepository patients;
  private final EncounterRepository encounters;
  private final DocumentValidator validator;
  private final ClinicalAccess access;
  private final AuditService audit;
  private final com.odontocare.treatments.repository.TreatmentPlanRepository plans;

  public PatientDocumentService(
      PatientDocumentRepository documents,
      DocumentContentRepository contents,
      DocumentCategoryRepository categories,
      DocumentPolicyRepository policies,
      DocumentConsentRepository consents,
      PatientRepository patients,
      EncounterRepository encounters,
      DocumentValidator validator,
      ClinicalAccess access,
      AuditService audit,
      com.odontocare.treatments.repository.TreatmentPlanRepository plans) {
    this.documents = documents;
    this.contents = contents;
    this.categories = categories;
    this.policies = policies;
    this.consents = consents;
    this.patients = patients;
    this.encounters = encounters;
    this.validator = validator;
    this.access = access;
    this.audit = audit;
    this.plans = plans;
  }

  @Transactional
  public PageResponse<DocumentResponse> list(
      UUID patientId,
      UUID categoryId,
      UUID encounterId,
      UUID planId,
      Integer tooth,
      String mediaType,
      java.time.LocalDate from,
      java.time.LocalDate to,
      PageQuery query) {
    if (mediaType != null
        && !Set.of("image/jpeg", "image/png", "image/webp", "application/pdf").contains(mediaType))
      throw ApiException.badRequest("Tipo de archivo no válido.");
    if (from != null && to != null && from.isAfter(to))
      throw ApiException.badRequest("El intervalo de fechas no es válido.");
    ClinicalAccess.requireTooth(tooth);
    var spec =
        SearchSpecifications.<PatientDocument>text(
                query.getSearch(), "fileName", "description", "categoryName", "actorName")
            .and(SearchSpecifications.equal("patientId", patientId))
            .and(SearchSpecifications.equal("categoryId", categoryId))
            .and(SearchSpecifications.equal("encounterId", encounterId))
            .and(SearchSpecifications.equal("planId", planId))
            .and(SearchSpecifications.equal("tooth", tooth))
            .and(SearchSpecifications.equal("mediaType", mediaType));
    if (from != null)
      spec = spec.and((root, cq, cb) -> cb.greaterThanOrEqualTo(root.get("recordedOn"), from));
    if (to != null)
      spec = spec.and((root, cq, cb) -> cb.lessThanOrEqualTo(root.get("recordedOn"), to));
    var page =
        documents.findAll(
            spec,
            query.pageable(
                Map.of(
                    "name",
                    "fileName",
                    "recordedOn",
                    "recordedOn",
                    "createdAt",
                    "createdAt",
                    "byteSize",
                    "byteSize")));
    audit.record("DOCUMENT_LIST_READ", "DOCUMENT", patientId, "Consultó metadatos documentales");
    return PageResponse.of(page.map(this::response));
  }

  @Transactional
  public DocumentResponse upload(UploadRequest request, MultipartFile file) {
    var patient = patients.findById(request.patientId()).orElseThrow(ApiException::notFound);
    if (!patient.getActive()) throw ApiException.badRequest("El paciente está inactivo.");
    access.requireDate(request.recordedOn());
    ClinicalAccess.requireTooth(request.tooth());
    if (request.encounterId() != null) {
      var encounter =
          encounters.findById(request.encounterId()).orElseThrow(ApiException::notFound);
      if (!encounter.getPatientId().equals(patient.getId()))
        throw ApiException.badRequest("La atención pertenece a otro paciente.");
    }
    if (request.planId() != null) {
      var plan = plans.findById(request.planId()).orElseThrow(ApiException::notFound);
      if (!plan.getPatientId().equals(request.patientId()))
        throw ApiException.badRequest("El tratamiento pertenece a otro paciente.");
    }
    var category = categories.findById(request.categoryId()).orElseThrow(ApiException::notFound);
    if (!category.getActive())
      throw ApiException.badRequest("La categoría documental está inactiva.");
    var validated =
        validator.validate(file, policies.findById((short) 1).orElseThrow().getMaxFileMiB());
    var document = new PatientDocument();
    document.setPatientId(patient.getId());
    document.setEncounterId(request.encounterId());
    document.setPlanId(request.planId());
    document.setTooth(request.tooth());
    document.setCategoryId(category.getId());
    document.setCategoryName(category.getName());
    document.setRecordedOn(request.recordedOn());
    document.setDescription(request.description().strip());
    document.setFileName(validated.name());
    document.setMediaType(validated.mediaType());
    document.setByteSize((long) validated.bytes().length);
    document.setSha256(hash(validated.bytes()));
    document.setActorName(access.actor().getDisplayName());
    documents.saveAndFlush(document);
    contents.save(new DocumentContent(document.getId(), validated.bytes()));
    audit.record(
        "DOCUMENT_UPLOADED",
        "DOCUMENT",
        document.getId(),
        "Guardó original documental (" + document.getByteSize() + " bytes)");
    return response(document);
  }

  @Transactional
  public Download download(UUID id, boolean attachment) {
    var document = documents.findById(id).orElseThrow(ApiException::notFound);
    byte[] bytes = contents.findById(id).orElseThrow(ApiException::notFound).getContent();
    audit.record(
        attachment ? "DOCUMENT_DOWNLOADED" : "DOCUMENT_VIEWED",
        "DOCUMENT",
        id,
        "Accedió al original documental");
    return new Download(document.getFileName(), document.getMediaType(), bytes);
  }

  @Transactional
  public PageResponse<ConsentResponse> consents(UUID patientId, PageQuery query) {
    var page =
        consents.findAll(
            SearchSpecifications.<DocumentConsent>text(
                    query.getSearch(), "name", "responsible", "relationship", "actorName")
                .and(SearchSpecifications.equal("patientId", patientId)),
            query.pageable(
                Map.of("name", "name", "signedOn", "signedOn", "createdAt", "createdAt")));
    audit.record("CONSENT_READ", "CONSENT", patientId, "Consultó consentimientos documentales");
    return PageResponse.of(page.map(this::consentResponse));
  }

  @Transactional
  public ConsentResponse createConsent(ConsentRequest request) {
    access.requireDate(request.signedOn());
    var document = documents.findById(request.documentId()).orElseThrow(ApiException::notFound);
    if (!document.getPatientId().equals(request.patientId()))
      throw ApiException.badRequest("El archivo del consentimiento pertenece a otro paciente.");
    var consent = new DocumentConsent();
    consent.setPatientId(request.patientId());
    consent.setDocumentId(document.getId());
    consent.setName(request.name().strip());
    consent.setResponsible(request.responsible().strip());
    consent.setRelationship(request.relationship().strip());
    consent.setSignedOn(request.signedOn());
    consent.setActorName(access.actor().getDisplayName());
    consents.saveAndFlush(consent);
    audit.record(
        "CONSENT_CREATED", "CONSENT", consent.getId(), "Registró consentimiento documental");
    return consentResponse(consent);
  }

  private String hash(byte[] bytes) {
    try {
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException(exception);
    }
  }

  private DocumentResponse response(PatientDocument document) {
    return new DocumentResponse(
        document.getId(),
        document.getPatientId(),
        document.getEncounterId(),
        document.getPlanId(),
        document.getTooth(),
        document.getCategoryId(),
        document.getCategoryName(),
        document.getRecordedOn(),
        document.getDescription(),
        document.getFileName(),
        document.getMediaType(),
        document.getByteSize(),
        document.getSha256(),
        document.getActorName(),
        document.getCreatedAt());
  }

  private ConsentResponse consentResponse(DocumentConsent consent) {
    return new ConsentResponse(
        consent.getId(),
        consent.getPatientId(),
        consent.getDocumentId(),
        consent.getName(),
        consent.getResponsible(),
        consent.getRelationship(),
        consent.getSignedOn(),
        consent.getActorName(),
        consent.getCreatedAt());
  }
}
