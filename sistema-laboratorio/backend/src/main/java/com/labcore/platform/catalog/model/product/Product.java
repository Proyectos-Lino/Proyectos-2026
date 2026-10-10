package com.labcore.platform.catalog;

import com.labcore.platform.common.BaseEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.Locale;
import java.util.UUID;

@Entity
@Table(name = "product")
public class Product extends BaseEntity {

  @Id private UUID id = UUID.randomUUID();
  private String code;
  private String name;
  private boolean active = true;

  protected Product() {}

  public Product(String code, String name) {
    update(code, name);
  }

  public UUID getId() {
    return id;
  }

  public String getCode() {
    return code;
  }

  public String getName() {
    return name;
  }

  public boolean isActive() {
    return active;
  }

  public void update(String code, String name) {
    this.code = code.trim().toUpperCase(Locale.ROOT);
    this.name = name.trim();
  }

  public void activate() {
    active = true;
  }

  public void deactivate() {
    active = false;
  }
}
