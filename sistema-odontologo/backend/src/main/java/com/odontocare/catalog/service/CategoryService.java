package com.odontocare.catalog.service;

import com.odontocare.audit.service.AuditService;
import com.odontocare.catalog.dto.*;
import com.odontocare.catalog.model.ServiceCategory;
import com.odontocare.catalog.repository.*;
import com.odontocare.installation.service.ConfigurationLock;
import com.odontocare.shared.pagination.*;
import com.odontocare.shared.web.ApiException;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CategoryService {
  private final ServiceCategoryRepository categories;
  private final DentalServiceRepository services;
  private final ConfigurationLock lock;
  private final AuditService audit;

  public CategoryService(
      ServiceCategoryRepository categories,
      DentalServiceRepository services,
      ConfigurationLock lock,
      AuditService audit) {
    this.categories = categories;
    this.services = services;
    this.lock = lock;
    this.audit = audit;
  }

  @Transactional(readOnly = true)
  public PageResponse<CategoryResponse> list(PageQuery query, Boolean active) {
    return PageResponse.of(
        categories
            .findAll(
                SearchSpecifications.<ServiceCategory>text(query.getSearch(), "name")
                    .and(SearchSpecifications.equal("active", active)),
                query.pageable(Map.of("name", "name", "createdAt", "createdAt")))
            .map(this::response));
  }

  @Transactional
  public CategoryResponse create(CategoryRequest request) {
    lock.acquire();
    if (categories.existsByNameIgnoreCase(request.name().strip()))
      throw ApiException.conflict("La categoría ya existe.");
    var category = new ServiceCategory();
    apply(category, request);
    categories.saveAndFlush(category);
    audit.record(
        "CATEGORY_CREATED",
        "CATEGORY",
        category.getId(),
        "Creó la categoría " + category.getName());
    return response(category);
  }

  @Transactional
  public CategoryResponse update(UUID id, CategoryRequest request) {
    lock.acquire();
    var category = categories.findById(id).orElseThrow(ApiException::notFound);
    category.checkVersion(request.version());
    if (categories.existsByNameIgnoreCaseAndIdNot(request.name().strip(), id))
      throw ApiException.conflict("La categoría ya existe.");
    if (!request.active() && services.existsByCategoryIdAndActiveTrue(id))
      throw ApiException.conflict(
          "Mueve o desactiva los servicios activos de esta categoría antes de desactivarla.");
    apply(category, request);
    categories.saveAndFlush(category);
    audit.record(
        "CATEGORY_UPDATED",
        "CATEGORY",
        id,
        "Actualizó la categoría "
            + category.getName()
            + ". Estado: "
            + (category.getActive() ? "activo" : "inactivo"));
    return response(category);
  }

  private void apply(ServiceCategory category, CategoryRequest request) {
    category.setName(request.name().strip());
    category.setActive(request.active());
  }

  private CategoryResponse response(ServiceCategory category) {
    return new CategoryResponse(
        category.getId(), category.getName(), category.getActive(), category.getVersion());
  }
}
