package com.labcore.platform.results;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ResultMatrixColumnRepository extends JpaRepository<ResultMatrixColumn, UUID> {
  List<ResultMatrixColumn> findByResultMatrixIdOrderByColumnIndexAsc(UUID matrixId);

  void deleteByResultMatrixIdAndColumnIndexGreaterThanEqual(UUID matrixId, int columnIndex);
}
