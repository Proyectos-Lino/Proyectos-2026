package com.labcore.platform.results;

import com.labcore.platform.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "result_matrix_column")
public class ResultMatrixColumn extends BaseEntity {
  @Id private UUID id = UUID.randomUUID();

  @Column(name = "result_matrix_id")
  private UUID resultMatrixId;

  private int columnIndex;
  private String carrierLabel;
  private BigDecimal distance;
  private BigDecimal diameter;
  private String shape;

  protected ResultMatrixColumn() {}

  public ResultMatrixColumn(
      UUID resultMatrixId,
      int columnIndex,
      String carrierLabel,
      BigDecimal distance,
      BigDecimal diameter,
      String shape) {
    this.resultMatrixId = resultMatrixId;
    this.columnIndex = columnIndex;
    replace(carrierLabel, distance, diameter, shape);
  }

  public UUID getId() {
    return id;
  }

  public UUID getResultMatrixId() {
    return resultMatrixId;
  }

  public int getColumnIndex() {
    return columnIndex;
  }

  public String getCarrierLabel() {
    return carrierLabel;
  }

  public BigDecimal getDistance() {
    return distance;
  }

  public BigDecimal getDiameter() {
    return diameter;
  }

  public String getShape() {
    return shape;
  }

  public void replace(String carrierLabel, BigDecimal distance, BigDecimal diameter, String shape) {
    this.carrierLabel = carrierLabel;
    this.distance = distance;
    this.diameter = diameter;
    this.shape = shape;
  }
}
