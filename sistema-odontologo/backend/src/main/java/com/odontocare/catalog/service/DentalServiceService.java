package com.odontocare.catalog.service;

import com.odontocare.audit.service.AuditService;
import com.odontocare.catalog.dto.*;
import com.odontocare.catalog.model.DentalService;
import com.odontocare.catalog.repository.*;
import com.odontocare.installation.service.ConfigurationLock;
import com.odontocare.shared.pagination.*;
import com.odontocare.shared.web.ApiException;
import java.util.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DentalServiceService {
  private final DentalServiceRepository services;
  private final ServiceCategoryRepository categories;
  private final ConfigurationLock lock;
  private final AuditService audit;

  public DentalServiceService(
      DentalServiceRepository services,
      ServiceCategoryRepository categories,
      ConfigurationLock lock,
      AuditService audit) {
    this.services = services;
    this.categories = categories;
    this.lock = lock;
    this.audit = audit;
  }

  @Transactional(readOnly = true)
  public PageResponse<ServiceResponse> list(
      PageQuery query, Boolean active, UUID categoryId, Boolean bookable, UUID dentistId) {
    Specification<DentalService> spec =
        SearchSpecifications.<DentalService>text(query.getSearch(), "name", "description")
            .and(SearchSpecifications.equal("active", active))
            .and(SearchSpecifications.equal("bookableByAgent", bookable));
    if (categoryId != null)
      spec = spec.and((root, q, cb) -> cb.equal(root.get("category").get("id"), categoryId));
    if (dentistId != null)
      spec =
          spec.and(
              (root, q, cb) -> {
                var sub = q.subquery(java.util.UUID.class);
                var d = sub.from(com.odontocare.dentists.model.Dentist.class);
                sub.select(d.get("id"))
                    .where(
                        cb.equal(d.get("id"), dentistId),
                        cb.equal(d.join("services").get("id"), root.get("id")));
                return cb.exists(sub);
              });
    return PageResponse.of(
        services
            .findAll(
                spec,
                query.pageable(
                    Map.of(
                        "name",
                        "name",
                        "price",
                        "price",
                        "duration",
                        "durationMinutes",
                        "createdAt",
                        "createdAt")))
            .map(this::response));
  }

  @Transactional(readOnly = true)
  public ServiceResponse get(UUID id) {
    return response(services.findById(id).orElseThrow(ApiException::notFound));
  }

  @Transactional
  public ServiceResponse create(ServiceRequest request) {
    lock.acquire();
    if (services.existsByNameIgnoreCase(request.name().strip()))
      throw ApiException.conflict("El servicio ya existe.");
    var service = new DentalService();
    apply(service, request);
    services.saveAndFlush(service);
    audit.record(
        "SERVICE_CREATED", "SERVICE", service.getId(), "Creó el servicio " + service.getName());
    return response(service);
  }

  @Transactional
  public ServiceResponse update(UUID id, ServiceRequest request) {
    lock.acquire();
    var service = services.findById(id).orElseThrow(ApiException::notFound);
    service.checkVersion(request.version());
    if (services.existsByNameIgnoreCaseAndIdNot(request.name().strip(), id))
      throw ApiException.conflict("El servicio ya existe.");
    apply(service, request);
    services.saveAndFlush(service);
    audit.record(
        "SERVICE_UPDATED",
        "SERVICE",
        id,
        "Actualizó precio, duración, reserva y estado del servicio " + service.getName());
    return response(service);
  }

  private void apply(DentalService service, ServiceRequest request) {
    var category =
        categories
            .findById(request.categoryId())
            .orElseThrow(() -> ApiException.badRequest("Selecciona una categoría existente."));
    if (request.active() && !category.getActive())
      throw ApiException.badRequest("Un servicio activo necesita una categoría activa.");
    service.setName(request.name().strip());
    service.setCategory(category);
    service.setPrice(request.price());
    service.setDurationMinutes(request.durationMinutes());
    service.setDescription(request.description().strip());
    service.setBookableByAgent(request.bookableByAgent());
    service.setActive(request.active());
  }

  private ServiceResponse response(DentalService service) {
    return new ServiceResponse(
        service.getId(),
        service.getName(),
        service.getCategory().getId(),
        service.getCategory().getName(),
        service.getPrice(),
        service.getDurationMinutes(),
        service.getDescription(),
        service.getBookableByAgent(),
        service.getActive(),
        service.getVersion());
  }
}
