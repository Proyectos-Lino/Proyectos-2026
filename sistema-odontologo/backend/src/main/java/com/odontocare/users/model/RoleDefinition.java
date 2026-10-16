package com.odontocare.users.model;

import com.odontocare.shared.web.ApiException;
import jakarta.persistence.*;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "role_definition")
public class RoleDefinition {
  @Id private String code;
  private String name;
  @Version private long version;

  @ElementCollection
  @CollectionTable(name = "role_permission", joinColumns = @JoinColumn(name = "role_code"))
  @Column(name = "permission")
  private Set<String> permissions = new HashSet<>();

  public String getCode() {
    return code;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public long getVersion() {
    return version;
  }

  public Set<String> getPermissions() {
    return permissions;
  }

  public void replacePermissions(Set<String> permissions) {
    this.permissions.clear();
    this.permissions.addAll(permissions);
  }

  public void checkVersion(long expected) {
    if (version != expected)
      throw ApiException.conflict("El rol cambió. Actualiza los datos antes de guardarlo.");
  }
}
