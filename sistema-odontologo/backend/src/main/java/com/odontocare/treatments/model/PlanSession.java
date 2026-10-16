package com.odontocare.treatments.model;

import com.odontocare.shared.model.VersionedEntity;
import jakarta.persistence.*;
import java.time.*;
import java.util.*;

@Entity
@Table(name = "treatment_session")
public class PlanSession extends VersionedEntity {
  private UUID planId;
  private UUID itemId;
  private UUID encounterId;
  private Integer procedureIndex;
  private Integer sessions;
  private String actorName;

  public UUID getPlanId() {
    return planId;
  }

  public void setPlanId(UUID value) {
    this.planId = value;
  }

  public UUID getItemId() {
    return itemId;
  }

  public void setItemId(UUID value) {
    this.itemId = value;
  }

  public UUID getEncounterId() {
    return encounterId;
  }

  public void setEncounterId(UUID value) {
    this.encounterId = value;
  }

  public Integer getProcedureIndex() {
    return procedureIndex;
  }

  public void setProcedureIndex(Integer value) {
    this.procedureIndex = value;
  }

  public Integer getSessions() {
    return sessions;
  }

  public void setSessions(Integer value) {
    this.sessions = value;
  }

  public String getActorName() {
    return actorName;
  }

  public void setActorName(String value) {
    this.actorName = value;
  }
}
