package com.odontocare.treatments.model;

import com.odontocare.shared.model.VersionedEntity;
import jakarta.persistence.*;
import java.time.*;
import java.util.*;

@Entity
@Table(name = "treatment_operation")
public class PlanOperation extends VersionedEntity {
  private UUID planId;
  private UUID requestKey;
  private String fingerprint;
  private String action;
  private String reason;
  private String actorName;
  private String summary;

  public UUID getPlanId() {
    return planId;
  }

  public void setPlanId(UUID value) {
    this.planId = value;
  }

  public UUID getRequestKey() {
    return requestKey;
  }

  public void setRequestKey(UUID value) {
    this.requestKey = value;
  }

  public String getFingerprint() {
    return fingerprint;
  }

  public void setFingerprint(String value) {
    this.fingerprint = value;
  }

  public String getAction() {
    return action;
  }

  public void setAction(String value) {
    this.action = value;
  }

  public String getReason() {
    return reason;
  }

  public void setReason(String value) {
    this.reason = value;
  }

  public String getActorName() {
    return actorName;
  }

  public void setActorName(String value) {
    this.actorName = value;
  }

  public String getSummary() {
    return summary;
  }

  public void setSummary(String value) {
    this.summary = value;
  }
}
