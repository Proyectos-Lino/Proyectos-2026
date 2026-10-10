package com.labcore.platform.catalog;

import com.labcore.platform.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "test_method")
public class TestMethod extends BaseEntity {

  @Id private UUID id = UUID.randomUUID();

  @Column(name = "test_definition_id")
  private UUID testDefinitionId;

  private String code;
  private String name;

  @Column(name = "version_label")
  private String versionLabel;

  private boolean active = true;

  protected TestMethod() {}

  public TestMethod(UUID testDefinitionId, String code, String name, String versionLabel) {
    this.testDefinitionId = testDefinitionId;
    this.code = code;
    this.name = name;
    this.versionLabel = versionLabel;
  }

  public UUID getId() {
    return id;
  }

  public UUID getTestDefinitionId() {
    return testDefinitionId;
  }

  public String getCode() {
    return code;
  }

  public String getName() {
    return name;
  }

  public String getVersionLabel() {
    return versionLabel;
  }

  public boolean isActive() {
    return active;
  }

  public void update(String code, String name, String versionLabel) {
    this.code = code;
    this.name = name;
    this.versionLabel = versionLabel;
  }

  public void activate() {
    active = true;
  }

  public void deactivate() {
    active = false;
  }
}
