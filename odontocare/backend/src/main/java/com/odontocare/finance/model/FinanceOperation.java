package com.odontocare.finance.model;

import com.odontocare.shared.model.VersionedEntity;
import jakarta.persistence.*;
import java.time.*;
import java.util.UUID;

@Entity
@Table(name = "finance_operation")
public class FinanceOperation extends VersionedEntity {
  private UUID requestKey;

  public UUID getRequestKey() {
    return requestKey;
  }

  public void setRequestKey(UUID value) {
    requestKey = value;
  }

  private String fingerprint;

  public String getFingerprint() {
    return fingerprint;
  }

  public void setFingerprint(String value) {
    fingerprint = value;
  }

  private String action;

  public String getAction() {
    return action;
  }

  public void setAction(String value) {
    action = value;
  }

  private UUID resultId;

  public UUID getResultId() {
    return resultId;
  }

  public void setResultId(UUID value) {
    resultId = value;
  }

  private String reason;

  public String getReason() {
    return reason;
  }

  public void setReason(String value) {
    reason = value;
  }

  private String actorName;

  public String getActorName() {
    return actorName;
  }

  public void setActorName(String value) {
    actorName = value;
  }
}
