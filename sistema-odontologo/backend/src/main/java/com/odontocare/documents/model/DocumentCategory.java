package com.odontocare.documents.model;

import com.odontocare.shared.model.VersionedEntity;
import jakarta.persistence.*;
import java.time.*;
import java.util.*;

@Entity
@Table(name = "document_category")
public class DocumentCategory extends VersionedEntity {
  private String name;
  private Boolean active = true;

  public String getName() {
    return name;
  }

  public void setName(String value) {
    this.name = value;
  }

  public Boolean getActive() {
    return active;
  }

  public void setActive(Boolean value) {
    this.active = value;
  }
}
