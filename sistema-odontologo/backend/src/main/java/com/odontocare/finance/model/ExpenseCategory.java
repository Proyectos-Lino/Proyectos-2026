package com.odontocare.finance.model;

import com.odontocare.shared.model.VersionedEntity;
import jakarta.persistence.*;
import java.time.*;

@Entity
@Table(name = "expense_category")
public class ExpenseCategory extends VersionedEntity {
  private String name;

  public String getName() {
    return name;
  }

  public void setName(String value) {
    name = value;
  }

  private Boolean active;

  public Boolean getActive() {
    return active;
  }

  public void setActive(Boolean value) {
    active = value;
  }
}
