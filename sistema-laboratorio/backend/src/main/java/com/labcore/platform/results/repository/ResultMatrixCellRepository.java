package com.labcore.platform.results;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ResultMatrixCellRepository extends JpaRepository<ResultMatrixCell, UUID> {
  List<ResultMatrixCell> findByResultMatrixIdOrderByRowIndexAscColumnIndexAsc(UUID matrixId);

  void deleteByResultMatrixIdAndRowIndexGreaterThanEqual(UUID matrixId, int rowIndex);

  void deleteByResultMatrixIdAndColumnIndexGreaterThanEqual(UUID matrixId, int columnIndex);
}
