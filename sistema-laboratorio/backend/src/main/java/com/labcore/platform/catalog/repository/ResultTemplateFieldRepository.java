package com.labcore.platform.catalog;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ResultTemplateFieldRepository extends JpaRepository<ResultTemplateField, UUID> {
  List<ResultTemplateField> findByResultTemplateIdOrderByDisplayOrderAsc(UUID resultTemplateId);

  void deleteByResultTemplateId(UUID resultTemplateId);
}
