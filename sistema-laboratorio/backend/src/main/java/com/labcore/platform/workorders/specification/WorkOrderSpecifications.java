package com.labcore.platform.workorders;

import com.labcore.platform.orders.ServiceOrder;
import java.time.Instant;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.jpa.domain.Specification;

final class WorkOrderSpecifications {
  private WorkOrderSpecifications() {}

  static Specification<WorkOrder> search(String search) {
    if (search == null || search.isBlank()) {
      return unrestricted();
    }
    String pattern = "%" + search.trim().toLowerCase(Locale.ROOT) + "%";
    return (root, query, builder) -> {
      var serviceOrderQuery = query.subquery(UUID.class);
      var serviceOrder = serviceOrderQuery.from(ServiceOrder.class);
      serviceOrderQuery
          .select(serviceOrder.get("id"))
          .where(builder.like(builder.lower(serviceOrder.get("code").as(String.class)), pattern));
      return builder.or(
          builder.like(builder.lower(root.get("code").as(String.class)), pattern),
          root.get("serviceOrderId").in(serviceOrderQuery));
    };
  }

  static Specification<WorkOrder> status(WorkOrderStatus status) {
    return status == null
        ? unrestricted()
        : (root, query, builder) -> builder.equal(root.get("status"), status);
  }

  static Specification<WorkOrder> area(UUID laboratoryAreaId) {
    return laboratoryAreaId == null
        ? unrestricted()
        : (root, query, builder) -> builder.equal(root.get("laboratoryAreaId"), laboratoryAreaId);
  }

  static Specification<WorkOrder> dueFrom(Instant dueFrom) {
    return dueFrom == null
        ? unrestricted()
        : (root, query, builder) ->
            builder.greaterThanOrEqualTo(root.get("laboratoryDueAt"), dueFrom);
  }

  static Specification<WorkOrder> dueTo(Instant dueTo) {
    return dueTo == null
        ? unrestricted()
        : (root, query, builder) -> builder.lessThanOrEqualTo(root.get("laboratoryDueAt"), dueTo);
  }

  static Specification<WorkOrder> createdFrom(Instant createdFrom) {
    return createdFrom == null
        ? unrestricted()
        : (root, query, builder) ->
            builder.greaterThanOrEqualTo(root.get("createdAt"), createdFrom);
  }

  static Specification<WorkOrder> createdTo(Instant createdTo) {
    return createdTo == null
        ? unrestricted()
        : (root, query, builder) -> builder.lessThanOrEqualTo(root.get("createdAt"), createdTo);
  }

  static Specification<WorkOrder> ids(Set<UUID> ids) {
    return (root, query, builder) -> root.get("id").in(ids);
  }

  private static Specification<WorkOrder> unrestricted() {
    return (root, query, builder) -> builder.conjunction();
  }
}
