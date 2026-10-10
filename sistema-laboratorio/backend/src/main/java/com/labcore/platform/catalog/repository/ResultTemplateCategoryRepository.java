package com.labcore.platform.catalog;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ResultTemplateCategoryRepository
    extends JpaRepository<ResultTemplateCategory, UUID> {
  List<ResultTemplateCategory> findByResultTemplateIdOrderByDisplayOrderAsc(UUID templateId);

  void deleteByResultTemplateId(UUID templateId);
}
