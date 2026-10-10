package com.labcore.platform.catalog;

import com.labcore.platform.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "result_template_field")
public class ResultTemplateField extends BaseEntity {

  @Id private UUID id = UUID.randomUUID();

  @Column(name = "result_template_id")
  private UUID resultTemplateId;

  @Column(name = "field_key")
  private String fieldKey;

  private String label;

  @Enumerated(EnumType.STRING)
  @Column(name = "result_type")
  private ResultDataType resultType;

  @Column(name = "measurement_unit_id")
  private UUID measurementUnitId;

  private boolean required;

  @Column(name = "display_order")
  private int displayOrder;

  @Enumerated(EnumType.STRING)
  private ResultFieldKind fieldKind = ResultFieldKind.CAPTURED;

  private boolean readOnly;
  private String formula;

  @Enumerated(EnumType.STRING)
  private CalculationScope calculationScope = CalculationScope.PER_REPETITION;

  private Integer decimalPrecision;
  private String informationText;
  private String defaultValue;

  protected ResultTemplateField() {}

  public ResultTemplateField(
      UUID resultTemplateId,
      String fieldKey,
      String label,
      ResultDataType resultType,
      UUID measurementUnitId,
      boolean required,
      int displayOrder,
      ResultFieldKind fieldKind,
      boolean readOnly,
      String formula,
      CalculationScope calculationScope,
      Integer decimalPrecision,
      String informationText,
      String defaultValue) {
    this.resultTemplateId = resultTemplateId;
    this.fieldKey = fieldKey.trim().toLowerCase(java.util.Locale.ROOT);
    this.label = label.trim();
    this.resultType = resultType;
    this.measurementUnitId = measurementUnitId;
    this.required = required;
    this.displayOrder = displayOrder;
    this.fieldKind = fieldKind;
    this.readOnly = readOnly || fieldKind != ResultFieldKind.CAPTURED;
    this.formula = formula;
    this.calculationScope =
        fieldKind == ResultFieldKind.CALCULATED
            ? calculationScope
            : CalculationScope.PER_REPETITION;
    this.decimalPrecision = decimalPrecision;
    this.informationText = informationText;
    this.defaultValue = defaultValue;
  }

  public UUID getId() {
    return id;
  }

  public UUID getResultTemplateId() {
    return resultTemplateId;
  }

  public String getFieldKey() {
    return fieldKey;
  }

  public String getLabel() {
    return label;
  }

  public ResultDataType getResultType() {
    return resultType;
  }

  public UUID getMeasurementUnitId() {
    return measurementUnitId;
  }

  public boolean isRequired() {
    return required;
  }

  public int getDisplayOrder() {
    return displayOrder;
  }

  public ResultFieldKind getFieldKind() {
    return fieldKind;
  }

  public boolean isReadOnly() {
    return readOnly;
  }

  public String getFormula() {
    return formula;
  }

  public CalculationScope getCalculationScope() {
    return calculationScope;
  }

  public Integer getDecimalPrecision() {
    return decimalPrecision;
  }

  public String getInformationText() {
    return informationText;
  }

  public String getDefaultValue() {
    return defaultValue;
  }
}
