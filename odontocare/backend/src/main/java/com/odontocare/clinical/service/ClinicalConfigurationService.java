package com.odontocare.clinical.service;

import com.odontocare.audit.service.AuditService;
import com.odontocare.clinical.dto.ClinicalContracts.*;
import com.odontocare.clinical.model.ClinicalTemplate;
import com.odontocare.clinical.repository.ClinicalTemplateRepository;
import com.odontocare.documents.dto.DocumentContracts.*;
import com.odontocare.documents.model.*;
import com.odontocare.documents.repository.*;
import com.odontocare.installation.service.ConfigurationLock;
import com.odontocare.shared.pagination.*;
import com.odontocare.shared.web.ApiException;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ClinicalConfigurationService {
  private final ClinicalTemplateRepository templates;
  private final DocumentCategoryRepository categories;
  private final DocumentPolicyRepository policies;
  private final PatientDocumentRepository documents;
  private final ConfigurationLock lock;
  private final AuditService audit;

  public ClinicalConfigurationService(
      ClinicalTemplateRepository templates,
      DocumentCategoryRepository categories,
      DocumentPolicyRepository policies,
      PatientDocumentRepository documents,
      ConfigurationLock lock,
      AuditService audit) {
    this.templates = templates;
    this.categories = categories;
    this.policies = policies;
    this.documents = documents;
    this.lock = lock;
    this.audit = audit;
  }

  @Transactional(readOnly = true)
  public PageResponse<TemplateResponse> templates(PageQuery query, Boolean active, String kind) {
    if (kind != null && !Set.of("ENCOUNTER", "BACKGROUND", "CONSENT").contains(kind))
      throw ApiException.badRequest("Tipo de plantilla no válido.");
    return PageResponse.of(
        templates
            .findAll(
                SearchSpecifications.<ClinicalTemplate>text(query.getSearch(), "name", "content")
                    .and(SearchSpecifications.equal("active", active))
                    .and(SearchSpecifications.equal("kind", kind)),
                query.pageable(Map.of("name", "name", "createdAt", "createdAt")))
            .map(this::templateResponse));
  }

  @Transactional
  public TemplateResponse saveTemplate(UUID id, TemplateRequest request) {
    lock.acquire();
    if (id == null
        ? templates.existsByNameIgnoreCase(request.name().strip())
        : templates.existsByNameIgnoreCaseAndIdNot(request.name().strip(), id))
      throw ApiException.conflict("Ya existe una plantilla con ese nombre.");
    var template =
        id == null
            ? new ClinicalTemplate()
            : templates.findById(id).orElseThrow(ApiException::notFound);
    if (id != null) template.checkVersion(request.version());
    template.setName(request.name().strip());
    template.setKind(request.kind());
    template.setContent(request.content());
    template.setActive(request.active());
    templates.saveAndFlush(template);
    audit.record(
        "CLINICAL_TEMPLATE_SAVED",
        "CLINICAL_TEMPLATE",
        template.getId(),
        "Guardó configuración de plantilla");
    return templateResponse(template);
  }

  @Transactional(readOnly = true)
  public PageResponse<CategoryResponse> categories(PageQuery query, Boolean active) {
    return PageResponse.of(
        categories
            .findAll(
                SearchSpecifications.<DocumentCategory>text(query.getSearch(), "name")
                    .and(SearchSpecifications.equal("active", active)),
                query.pageable(Map.of("name", "name", "createdAt", "createdAt")))
            .map(
                category ->
                    new CategoryResponse(
                        category.getId(),
                        category.getName(),
                        category.getActive(),
                        category.getVersion())));
  }

  @Transactional
  public CategoryResponse saveCategory(UUID id, CategoryRequest request) {
    lock.acquire();
    if (id == null
        ? categories.existsByNameIgnoreCase(request.name().strip())
        : categories.existsByNameIgnoreCaseAndIdNot(request.name().strip(), id))
      throw ApiException.conflict("Ya existe una categoría documental con ese nombre.");
    var category =
        id == null
            ? new DocumentCategory()
            : categories.findById(id).orElseThrow(ApiException::notFound);
    if (id != null) category.checkVersion(request.version());
    category.setName(request.name().strip());
    category.setActive(request.active());
    categories.saveAndFlush(category);
    audit.record(
        "DOCUMENT_CATEGORY_SAVED",
        "DOCUMENT_CATEGORY",
        category.getId(),
        "Guardó categoría documental");
    return new CategoryResponse(
        category.getId(), category.getName(), category.getActive(), category.getVersion());
  }

  @Transactional(readOnly = true)
  public PolicyResponse policy() {
    var policy = policies.findById((short) 1).orElseThrow();
    return new PolicyResponse(
        policy.getMaxFileMiB(), policy.getVersion(), documents.totalBytes(), documents.count());
  }

  @Transactional
  public PolicyResponse savePolicy(PolicyRequest request) {
    lock.acquire();
    var policy = policies.findById((short) 1).orElseThrow();
    if (policy.getVersion() != request.version())
      throw ApiException.conflict("La política cambió. Actualiza antes de guardar.");
    policy.setMaxFileMiB(request.maxFileMiB());
    policies.saveAndFlush(policy);
    audit.record(
        "DOCUMENT_POLICY_SAVED",
        "DOCUMENT_POLICY",
        1,
        "Límite de archivos: " + request.maxFileMiB() + " MiB");
    return policy();
  }

  @Transactional(readOnly = true)
  public TemplateResponse template(UUID id) {
    return templateResponse(templates.findById(id).orElseThrow(ApiException::notFound));
  }

  private TemplateResponse templateResponse(ClinicalTemplate template) {
    return new TemplateResponse(
        template.getId(),
        template.getName(),
        template.getKind(),
        template.getContent(),
        template.getActive(),
        template.getVersion());
  }
}
