package com.labcore.platform.catalog;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface ProductVarietyRepository
    extends JpaRepository<ProductVariety, UUID>, JpaSpecificationExecutor<ProductVariety> {
  boolean existsByProductIdAndCodeIgnoreCase(UUID productId, String code);

  boolean existsByProductIdAndCodeIgnoreCaseAndIdNot(UUID productId, String code, UUID id);

  List<ProductVariety> findByProductIdAndActiveTrueOrderByNameAsc(UUID productId);
}
