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
@Table(name = "result_template")
public class ResultTemplate extends BaseEntity {

  @Id private UUID id = UUID.randomUUID();

  @Column(name = "test_definition_id")
  private UUID testDefinitionId;

  @Column(name = "sample_type_id")
  private UUID sampleTypeId;

  @Enumerated(EnumType.STRING)
  private ResultMode resultMode = ResultMode.SCALAR;

  private String name;
  private String description;
  private int minimumRepetitions = 1;
  private Integer maximumRepetitions;
  private int defaultRows = 1;
  private Integer maximumRows;
  private int defaultColumns = 1;
  private Integer maximumColumns;
  private boolean active = true;

  protected ResultTemplate() {}

  public ResultTemplate(
      UUID testDefinitionId,
      UUID sampleTypeId,
      String name,
      String description,
      ResultMode resultMode) {
    this.testDefinitionId = testDefinitionId;
    this.sampleTypeId = sampleTypeId;
    update(name, description, resultMode, 1, null, 1, null, 1, null);
  }

  public UUID getId() {
    return id;
  }

  public UUID getTestDefinitionId() {
    return testDefinitionId;
  }

  public UUID getSampleTypeId() {
    return sampleTypeId;
  }

  public ResultMode getResultMode() {
    return resultMode;
  }

  public String getName() {
    return name;
  }

  public String getDescription() {
    return description;
  }

  public int getMinimumRepetitions() {
    return minimumRepetitions;
  }

  public Integer getMaximumRepetitions() {
    return maximumRepetitions;
  }

  public int getDefaultRows() {
    return defaultRows;
  }

  public Integer getMaximumRows() {
    return maximumRows;
  }

  public int getDefaultColumns() {
    return defaultColumns;
  }

  public Integer getMaximumColumns() {
    return maximumColumns;
  }

  public boolean isActive() {
    return active;
  }

  public void update(
      String name,
      String description,
      ResultMode resultMode,
      int minimumRepetitions,
      Integer maximumRepetitions,
      int defaultRows,
      Integer maximumRows,
      int defaultColumns,
      Integer maximumColumns) {
    this.name = name.trim();
    this.description = description == null || description.isBlank() ? null : description.trim();
    this.resultMode = resultMode;
    this.minimumRepetitions = minimumRepetitions;
    this.maximumRepetitions = maximumRepetitions;
    this.defaultRows = defaultRows;
    this.maximumRows = maximumRows;
    this.defaultColumns = defaultColumns;
    this.maximumColumns = maximumColumns;
  }

  public void activate() {
    active = true;
  }

  public void deactivate() {
    active = false;
  }
}
