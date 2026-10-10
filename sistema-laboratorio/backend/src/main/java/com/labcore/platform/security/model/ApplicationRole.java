package com.labcore.platform.security;

import com.labcore.platform.common.BaseEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "app_role")
public class ApplicationRole extends BaseEntity {

  @Id private UUID id = UUID.randomUUID();

  @Enumerated(EnumType.STRING)
  private RoleCode code;

  private String name;

  protected ApplicationRole() {}

  public ApplicationRole(RoleCode code, String name) {
    this.code = code;
    this.name = name;
  }

  public UUID getId() {
    return id;
  }

  public RoleCode getCode() {
    return code;
  }
}
