package com.labcore.platform.results;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "result_matrix_change_log")
public class ResultMatrixChangeLog {
  @Id private UUID id = UUID.randomUUID();

  @Column(name = "result_matrix_id")
  private UUID resultMatrixId;

  private String targetType;
  private Integer rowIndex;
  private Integer columnIndex;
  private String fieldName;
  private String oldValue;
  private String newValue;
  private String reason;

  @Column(name = "changed_by")
  private UUID changedBy;

  private String changedByRole;

  private Instant changedAt;

  protected ResultMatrixChangeLog() {}

  public UUID getId() {
    return id;
  }

  public UUID getResultMatrixId() {
    return resultMatrixId;
  }

  public String getTargetType() {
    return targetType;
  }

  public Integer getRowIndex() {
    return rowIndex;
  }

  public Integer getColumnIndex() {
    return columnIndex;
  }

  public String getFieldName() {
    return fieldName;
  }

  public String getOldValue() {
    return oldValue;
  }

  public String getNewValue() {
    return newValue;
  }

  public String getReason() {
    return reason;
  }

  public UUID getChangedBy() {
    return changedBy;
  }

  public Instant getChangedAt() {
    return changedAt;
  }

  public ResultMatrixChangeLog(
      UUID resultMatrixId,
      String targetType,
      Integer rowIndex,
      Integer columnIndex,
      String fieldName,
      String oldValue,
      String newValue,
      String reason,
      UUID changedBy,
      String changedByRole) {
    this.resultMatrixId = resultMatrixId;
    this.targetType = targetType;
    this.rowIndex = rowIndex;
    this.columnIndex = columnIndex;
    this.fieldName = fieldName;
    this.oldValue = oldValue;
    this.newValue = newValue;
    this.reason = reason;
    this.changedBy = changedBy;
    this.changedByRole = changedByRole;
    this.changedAt = Instant.now();
  }

  public String getChangedByRole() {
    return changedByRole;
  }
}
