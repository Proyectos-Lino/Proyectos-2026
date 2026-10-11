package com.labcore.platform.results;

import com.labcore.platform.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "result_matrix_cell")
public class ResultMatrixCell extends BaseEntity {
  @Id private UUID id = UUID.randomUUID();

  @Column(name = "result_matrix_id")
  private UUID resultMatrixId;

  private int rowIndex;
  private int columnIndex;
  private String valueText;
  private BigDecimal valueNumber;

  @Column(name = "category_code")
  private String category;

  protected ResultMatrixCell() {}

  public ResultMatrixCell(
      UUID resultMatrixId,
      int rowIndex,
      int columnIndex,
      String valueText,
      BigDecimal valueNumber,
      String category) {
    this.resultMatrixId = resultMatrixId;
    this.rowIndex = rowIndex;
    this.columnIndex = columnIndex;
    replace(valueText, valueNumber, category);
  }

  public UUID getId() {
    return id;
  }

  public UUID getResultMatrixId() {
    return resultMatrixId;
  }

  public int getRowIndex() {
    return rowIndex;
  }

  public int getColumnIndex() {
    return columnIndex;
  }

  public String getValueText() {
    return valueText;
  }

  public BigDecimal getValueNumber() {
    return valueNumber;
  }

  public String getCategory() {
    return category;
  }

  public void replace(String valueText, BigDecimal valueNumber, String category) {
    this.valueText = valueText;
    this.valueNumber = valueNumber;
    this.category = category;
  }
}
