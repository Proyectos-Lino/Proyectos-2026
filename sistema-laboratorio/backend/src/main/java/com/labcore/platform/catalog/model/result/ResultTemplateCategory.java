package com.labcore.platform.catalog;

import com.labcore.platform.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.Locale;
import java.util.UUID;

@Entity
@Table(name = "result_template_category")
public class ResultTemplateCategory extends BaseEntity {

  @Id private UUID id = UUID.randomUUID();

  @Column(name = "result_template_id")
  private UUID resultTemplateId;

  private String code;
  private String label;
  private int displayOrder;
  private String legend;

  protected ResultTemplateCategory() {}

  public ResultTemplateCategory(
      UUID resultTemplateId, String code, String label, int displayOrder, String legend) {
    this.resultTemplateId = resultTemplateId;
    this.code = code.trim().toUpperCase(Locale.ROOT);
    this.label = label.trim();
    this.displayOrder = displayOrder;
    this.legend = legend == null || legend.isBlank() ? null : legend.trim();
  }

  public UUID getId() {
    return id;
  }

  public UUID getResultTemplateId() {
    return resultTemplateId;
  }

  public String getCode() {
    return code;
  }

  public String getLabel() {
    return label;
  }

  public String getLegend() {
    return legend;
  }

  public int getDisplayOrder() {
    return displayOrder;
  }
}
