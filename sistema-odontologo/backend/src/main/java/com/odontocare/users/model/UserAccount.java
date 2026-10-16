package com.odontocare.users.model;

import com.odontocare.shared.model.VersionedEntity;
import jakarta.persistence.*;
import java.util.*;

@Entity
@Table(name = "user_account")
public class UserAccount extends VersionedEntity {
  @Column(nullable = false, length = 60)
  private String username;

  @Column(name = "display_name", nullable = false, length = 120)
  private String displayName;

  @Column(nullable = false, length = 160)
  private String email = "";

  @Column(name = "password_hash", nullable = false, length = 100)
  private String passwordHash;

  @Column(nullable = false)
  private boolean active = true;

  @Column(name = "auth_version", nullable = false)
  private long authVersion;

  @ManyToMany(fetch = FetchType.LAZY)
  @JoinTable(
      name = "user_role",
      joinColumns = @JoinColumn(name = "user_id"),
      inverseJoinColumns = @JoinColumn(name = "role_code"))
  private Set<RoleDefinition> roles = new HashSet<>();

  public String getUsername() {
    return username;
  }

  public void setUsername(String username) {
    this.username = username;
  }

  public String getDisplayName() {
    return displayName;
  }

  public void setDisplayName(String displayName) {
    this.displayName = displayName;
  }

  public String getEmail() {
    return email;
  }

  public void setEmail(String email) {
    this.email = email;
  }

  public String getPasswordHash() {
    return passwordHash;
  }

  public void setPasswordHash(String passwordHash) {
    this.passwordHash = passwordHash;
  }

  public boolean getActive() {
    return active;
  }

  public void setActive(boolean active) {
    this.active = active;
  }

  public long getAuthVersion() {
    return authVersion;
  }

  public void setAuthVersion(long authVersion) {
    this.authVersion = authVersion;
  }

  public Set<RoleDefinition> getRoles() {
    return roles;
  }

  public void setRoles(Set<RoleDefinition> roles) {
    this.roles = roles;
  }
}
