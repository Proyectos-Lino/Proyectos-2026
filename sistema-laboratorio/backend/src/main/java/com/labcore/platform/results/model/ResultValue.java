package com.labcore.platform.results;

import com.labcore.platform.catalog.ResultFieldKind;
import com.labcore.platform.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "result_value")
public class ResultValue extends BaseEntity {
  @Id private UUID id = UUID.randomUUID();

  @Column(name = "result_entry_id")
  private UUID resultEntryId;

  private String fieldKey;
  private String label;
  private int position;
  private int repetitionIndex;
  private String valueText;
  private BigDecimal valueNumber;

  @Enumerated(EnumType.STRING)
  private ResultFieldKind fieldKind = ResultFieldKind.CAPTURED;

  private String formulaUsed;
  private String calculationInputs;

  @Column(name = "measurement_unit_id")
  private UUID measurementUnitId;

  protected ResultValue() {}

  public ResultValue(
      UUID resultEntryId,
      String fieldKey,
      String label,
      int position,
      String valueText,
      BigDecimal valueNumber,
      UUID measurementUnitId) {
    this(resultEntryId, fieldKey, label, position, 0, valueText, valueNumber, measurementUnitId);
  }

  public ResultValue(
      UUID resultEntryId,
      String fieldKey,
      String label,
      int position,
      int repetitionIndex,
      String valueText,
      BigDecimal valueNumber,
      UUID measurementUnitId) {
    this.resultEntryId = resultEntryId;
    this.fieldKey = fieldKey;
    this.label = label;
    this.position = position;
    this.repetitionIndex = repetitionIndex;
    this.valueText = valueText;
    this.valueNumber = valueNumber;
    this.measurementUnitId = measurementUnitId;
  }

  public UUID getId() {
    return id;
  }

  public UUID getResultEntryId() {
    return resultEntryId;
  }

  public String getFieldKey() {
    return fieldKey;
  }

  public String getLabel() {
    return label;
  }

  public int getPosition() {
    return position;
  }

  public int getRepetitionIndex() {
    return repetitionIndex;
  }

  public String getValueText() {
    return valueText;
  }

  public BigDecimal getValueNumber() {
    return valueNumber;
  }

  public UUID getMeasurementUnitId() {
    return measurementUnitId;
  }

  public ResultFieldKind getFieldKind() {
    return fieldKind;
  }

  public String getFormulaUsed() {
    return formulaUsed;
  }

  public String getCalculationInputs() {
    return calculationInputs;
  }

  public void markCalculated(String formula, String inputs) {
    fieldKind = ResultFieldKind.CALCULATED;
    formulaUsed = formula;
    calculationInputs = inputs;
  }

  public void replace(
      String label, int position, String valueText, BigDecimal valueNumber, UUID unitId) {
    this.label = label;
    this.position = position;
    this.valueText = valueText;
    this.valueNumber = valueNumber;
    this.measurementUnitId = unitId;
  }
}
