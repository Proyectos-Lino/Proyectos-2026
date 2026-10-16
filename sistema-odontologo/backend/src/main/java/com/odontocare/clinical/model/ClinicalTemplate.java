package com.odontocare.clinical.model;

import com.odontocare.shared.model.VersionedEntity;
import jakarta.persistence.*;
import java.time.*;
import java.util.*;

@Entity
@Table(name = "clinical_template")
public class ClinicalTemplate extends VersionedEntity {
  private String name;
  private String kind;

  @Column(columnDefinition = "text")
  private String content;

  private Boolean active = true;

  public String getName() {
    return name;
  }

  public void setName(String value) {
    this.name = value;
  }

  public String getKind() {
    return kind;
  }

  public void setKind(String value) {
    this.kind = value;
  }

  public String getContent() {
    return content;
  }

  public void setContent(String value) {
    this.content = value;
  }

  public Boolean getActive() {
    return active;
  }

  public void setActive(Boolean value) {
    this.active = value;
  }
}
