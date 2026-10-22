package com.odontocare.documents.repository;

import com.odontocare.documents.model.DocumentCategory;
import java.util.*;
import org.springframework.data.jpa.repository.*;

public interface DocumentCategoryRepository
    extends JpaRepository<DocumentCategory, UUID>, JpaSpecificationExecutor<DocumentCategory> {
  boolean existsByNameIgnoreCaseAndIdNot(String name, UUID id);

  boolean existsByNameIgnoreCase(String name);
}
