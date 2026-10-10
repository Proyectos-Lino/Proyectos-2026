package com.labcore.platform.catalog;

import java.util.Locale;
import org.springframework.data.jpa.domain.Specification;

final class CatalogSpecifications {

  private CatalogSpecifications() {}

  static <T> Specification<T> textSearch(String search, String... attributes) {
    if (search == null || search.isBlank()) {
      return (root, query, builder) -> builder.conjunction();
    }

    String pattern = "%" + search.trim().toLowerCase(Locale.ROOT) + "%";
    return (root, query, builder) ->
        builder.or(
            java.util.Arrays.stream(attributes)
                .map(
                    attribute ->
                        builder.like(builder.lower(root.get(attribute).as(String.class)), pattern))
                .toArray(jakarta.persistence.criteria.Predicate[]::new));
  }

  static <T> Specification<T> active(Boolean active) {
    if (active == null) {
      return (root, query, builder) -> builder.conjunction();
    }
    return (root, query, builder) -> builder.equal(root.get("active"), active);
  }

  static <T> Specification<T> equalTo(String attribute, Object value) {
    return (root, query, builder) -> builder.equal(root.get(attribute), value);
  }
}
