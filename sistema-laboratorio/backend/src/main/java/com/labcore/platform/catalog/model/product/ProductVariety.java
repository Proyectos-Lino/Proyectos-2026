package com.labcore.platform.catalog;

import com.labcore.platform.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.Locale;
import java.util.UUID;

@Entity
@Table(name = "product_variety")
public class ProductVariety extends BaseEntity {

  @Id private UUID id = UUID.randomUUID();

  @Column(name = "product_id")
  private UUID productId;

  private String code;
  private String name;
  private boolean active = true;

  protected ProductVariety() {}

  public ProductVariety(UUID productId, String code, String name) {
    this.productId = productId;
    update(code, name);
  }

  public UUID getId() {
    return id;
  }

  public UUID getProductId() {
    return productId;
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
