package com.labcore.platform.results;

import com.labcore.platform.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "result_matrix")
public class ResultMatrix extends BaseEntity {
  @Id private UUID id = UUID.randomUUID();

  @Column(name = "result_entry_id")
  private UUID resultEntryId;

  private int rowCount;
  private int columnCount;

  @Enumerated(EnumType.STRING)
  private MatrixExecutionStatus executionStatus;

  private int declaredRowCount;
  private int declaredColumnCount;
  private Instant startedAt;
  private Instant completedAt;
  private Instant lastModifiedAt;

  @Column(name = "last_modified_by")
  private UUID lastModifiedBy;

  protected ResultMatrix() {}

  public ResultMatrix(UUID resultEntryId, int rowCount, int columnCount) {
    this(resultEntryId, rowCount, columnCount, null, true);
  }

  public ResultMatrix(
      UUID resultEntryId, int rowCount, int columnCount, UUID actorId, boolean completed) {
    this.resultEntryId = resultEntryId;
    resize(rowCount, columnCount);
    this.declaredRowCount = rowCount;
    this.declaredColumnCount = columnCount;
    this.executionStatus =
        completed ? MatrixExecutionStatus.COMPLETED : MatrixExecutionStatus.IN_PROGRESS;
    this.startedAt = Instant.now();
    this.completedAt = completed ? startedAt : null;
    this.lastModifiedAt = startedAt;
    this.lastModifiedBy = actorId;
  }

  public UUID getId() {
    return id;
  }

  public UUID getResultEntryId() {
    return resultEntryId;
  }

  public int getRowCount() {
    return rowCount;
  }

  public int getColumnCount() {
    return columnCount;
  }

  public MatrixExecutionStatus getExecutionStatus() {
    return executionStatus;
  }

  public int getDeclaredRowCount() {
    return declaredRowCount;
  }

  public int getDeclaredColumnCount() {
    return declaredColumnCount;
  }

  public Instant getStartedAt() {
    return startedAt;
  }

  public Instant getCompletedAt() {
    return completedAt;
  }

  public Instant getLastModifiedAt() {
    return lastModifiedAt;
  }

  public UUID getLastModifiedBy() {
    return lastModifiedBy;
  }

  public void resize(int rowCount, int columnCount) {
    this.rowCount = rowCount;
    this.columnCount = columnCount;
  }

  public void adjustDeclaration(int rowCount, int columnCount, UUID actorId) {
    resize(rowCount, columnCount);
    declaredRowCount = rowCount;
    declaredColumnCount = columnCount;
    touch(actorId);
  }

  public void touch(UUID actorId) {
    lastModifiedAt = Instant.now();
    lastModifiedBy = actorId;
  }

  public void complete(UUID actorId) {
    executionStatus = MatrixExecutionStatus.COMPLETED;
    completedAt = Instant.now();
    touch(actorId);
  }
}
