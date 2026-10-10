package com.labcore.platform.security;

import com.labcore.platform.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.Table;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "app_user")
public class ApplicationUser extends BaseEntity {

  @Id private UUID id = UUID.randomUUID();

  @Column(name = "full_name")
  private String fullName;

  private String email;

  @Column(name = "password_hash")
  private String passwordHash;

  private boolean active = true;

  @Column(name = "laboratory_area_id")
  private UUID laboratoryAreaId;

  @ManyToMany(fetch = FetchType.EAGER)
  @JoinTable(
      name = "app_user_role",
      joinColumns = @JoinColumn(name = "user_id"),
      inverseJoinColumns = @JoinColumn(name = "role_id"))
  private Set<ApplicationRole> roles = new HashSet<>();

  protected ApplicationUser() {}

  public ApplicationUser(
      String fullName, String email, String passwordHash, Set<ApplicationRole> roles) {
    this(fullName, email, passwordHash, roles, null);
  }

  public ApplicationUser(
      String fullName,
      String email,
      String passwordHash,
      Set<ApplicationRole> roles,
      UUID laboratoryAreaId) {
    this.fullName = fullName;
    this.email = email.toLowerCase();
    this.passwordHash = passwordHash;
    this.roles = new HashSet<>(roles);
    this.laboratoryAreaId = laboratoryAreaId;
  }

  public UUID getId() {
    return id;
  }

  public String getFullName() {
    return fullName;
  }

  public String getEmail() {
    return email;
  }

  public String getPasswordHash() {
    return passwordHash;
  }

  public boolean isActive() {
    return active;
  }

  public UUID getLaboratoryAreaId() {
    return laboratoryAreaId;
  }

  public Set<ApplicationRole> getRoles() {
    return Set.copyOf(roles);
  }

  public boolean hasRole(RoleCode roleCode) {
    return roles.stream().anyMatch(role -> role.getCode() == roleCode);
  }

  public void update(
      String fullName, String email, Set<ApplicationRole> roles, UUID laboratoryAreaId) {
    this.fullName = fullName;
    this.email = email.toLowerCase(java.util.Locale.ROOT);
    this.roles = new HashSet<>(roles);
    this.laboratoryAreaId = laboratoryAreaId;
  }

  public void updatePassword(String passwordHash) {
    this.passwordHash = passwordHash;
  }

  public void activate() {
    active = true;
  }

  public void deactivate() {
    active = false;
  }
}
