package com.odontocare.shared.pagination;

import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import java.util.Locale;
import org.springframework.data.jpa.domain.Specification;

public final class SearchSpecifications {
  private SearchSpecifications() {}

  public static <T> Specification<T> text(String value, String... fields) {
    return (root, query, cb) -> {
      if (value == null || value.isBlank()) return cb.conjunction();
      String escaped =
          value
              .strip()
              .toLowerCase(Locale.ROOT)
              .replace("\\", "\\\\")
              .replace("%", "\\%")
              .replace("_", "\\_");
      Predicate[] predicates = new Predicate[fields.length];
      for (int index = 0; index < fields.length; index++) {
        Path<?> expression = root;
        for (String segment : fields[index].split("\\.")) expression = expression.get(segment);
        predicates[index] =
            cb.like(cb.lower(expression.as(String.class)), "%" + escaped + "%", '\\');
      }
      return cb.or(predicates);
    };
  }

  public static <T> Specification<T> equal(String field, Object value) {
    return (root, query, cb) -> value == null ? cb.conjunction() : cb.equal(root.get(field), value);
  }
}
