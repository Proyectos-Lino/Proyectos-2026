package com.labcore.platform.reports;

import com.labcore.platform.orders.ServiceOrder;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;
import org.springframework.data.jpa.domain.Specification;

final class LaboratoryReportSpecifications {
  private LaboratoryReportSpecifications() {}

  static Specification<LaboratoryReport> filteredBy(
      String search,
      LaboratoryReportStatus status,
      UUID serviceOrderId,
      Instant issuedFrom,
      Instant issuedTo,
      UUID laboratoryAreaId) {
    return ((Specification<LaboratoryReport>)
            (root, query, criteriaBuilder) -> criteriaBuilder.conjunction())
        .and(codeContains(search))
        .and(hasStatus(status))
        .and(hasServiceOrder(serviceOrderId))
        .and(issuedFrom(issuedFrom))
        .and(issuedTo(issuedTo))
        .and(inArea(laboratoryAreaId));
  }

  private static Specification<LaboratoryReport> codeContains(String search) {
    if (search == null || search.isBlank()) {
      return null;
    }
    String pattern = "%" + search.trim().toLowerCase(Locale.ROOT) + "%";
    return (root, query, criteriaBuilder) ->
        criteriaBuilder.like(criteriaBuilder.lower(root.get("code")), pattern);
  }

  private static Specification<LaboratoryReport> hasStatus(LaboratoryReportStatus status) {
    return status == null
        ? null
        : (root, query, criteriaBuilder) -> criteriaBuilder.equal(root.get("status"), status);
  }

  private static Specification<LaboratoryReport> hasServiceOrder(UUID serviceOrderId) {
    return serviceOrderId == null
        ? null
        : (root, query, criteriaBuilder) ->
            criteriaBuilder.equal(root.get("serviceOrderId"), serviceOrderId);
  }

  private static Specification<LaboratoryReport> issuedFrom(Instant issuedFrom) {
    return issuedFrom == null
        ? null
        : (root, query, criteriaBuilder) ->
            criteriaBuilder.greaterThanOrEqualTo(root.get("issuedAt"), issuedFrom);
  }

  private static Specification<LaboratoryReport> issuedTo(Instant issuedTo) {
    return issuedTo == null
        ? null
        : (root, query, criteriaBuilder) ->
            criteriaBuilder.lessThan(root.get("issuedAt"), issuedTo);
  }

  private static Specification<LaboratoryReport> inArea(UUID laboratoryAreaId) {
    if (laboratoryAreaId == null) {
      return null;
    }
    return (root, query, criteriaBuilder) -> {
      var serviceOrders = query.subquery(UUID.class);
      var serviceOrder = serviceOrders.from(ServiceOrder.class);
      serviceOrders
          .select(serviceOrder.get("id"))
          .where(criteriaBuilder.equal(serviceOrder.get("laboratoryAreaId"), laboratoryAreaId));
      return root.get("serviceOrderId").in(serviceOrders);
    };
  }
}
