package com.labcore.platform.results;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "result_calculation_snapshot")
public class ResultCalculationSnapshot {
  @Id private UUID id = UUID.randomUUID();

  @Column(name = "result_entry_id")
  private UUID resultEntryId;

  private String fieldKey;
  private int repetitionIndex;
  private int resultRevision;
  private String formulaUsed;
  private String calculationInputs;
  private BigDecimal calculatedValue;
  private UUID calculatedBy;
  private String calculatedByRole;
  private Instant calculatedAt;

  protected ResultCalculationSnapshot() {}

  public ResultCalculationSnapshot(
      UUID resultEntryId,
      String fieldKey,
      int repetitionIndex,
      int resultRevision,
      String formulaUsed,
      String calculationInputs,
      BigDecimal calculatedValue,
      UUID calculatedBy,
      String calculatedByRole) {
    this.resultEntryId = resultEntryId;
    this.fieldKey = fieldKey;
    this.repetitionIndex = repetitionIndex;
    this.resultRevision = resultRevision;
    this.formulaUsed = formulaUsed;
    this.calculationInputs = calculationInputs;
    this.calculatedValue = calculatedValue;
    this.calculatedBy = calculatedBy;
    this.calculatedByRole = calculatedByRole;
    this.calculatedAt = Instant.now();
  }

  public UUID getId() {
    return id;
  }

  public String getFieldKey() {
    return fieldKey;
  }

  public int getRepetitionIndex() {
    return repetitionIndex;
  }

  public int getResultRevision() {
    return resultRevision;
  }

  public String getFormulaUsed() {
    return formulaUsed;
  }

  public String getCalculationInputs() {
    return calculationInputs;
  }

  public BigDecimal getCalculatedValue() {
    return calculatedValue;
  }

  public UUID getCalculatedBy() {
    return calculatedBy;
  }

  public String getCalculatedByRole() {
    return calculatedByRole;
  }

  public Instant getCalculatedAt() {
    return calculatedAt;
  }
}
