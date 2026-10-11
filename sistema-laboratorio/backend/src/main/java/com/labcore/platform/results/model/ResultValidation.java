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
@Table(name = "result_validation")
public class ResultValidation extends BaseEntity {
  @Id private UUID id = UUID.randomUUID();

  @Column(name = "result_entry_id")
  private UUID resultEntryId;

  @Enumerated(EnumType.STRING)
  private ResultValidationStatus status = ResultValidationStatus.APPROVED;

  @Column(name = "validator_id")
  private UUID validatorId;

  private Instant validatedAt;
  private String comment;
  private String validatorRole;
  private int resultRevision;

  protected ResultValidation() {}

  public ResultValidation(
      UUID resultEntryId,
      UUID validatorId,
      String validatorRole,
      int resultRevision,
      String comment) {
    this.resultEntryId = resultEntryId;
    this.validatorId = validatorId;
    this.validatorRole = validatorRole;
    this.resultRevision = resultRevision;
    this.comment = comment;
    this.validatedAt = Instant.now();
  }

  public UUID getValidatorId() {
    return validatorId;
  }

  public Instant getValidatedAt() {
    return validatedAt;
  }

  public String getComment() {
    return comment;
  }

  public String getValidatorRole() {
    return validatorRole;
  }

  public int getResultRevision() {
    return resultRevision;
  }
}
