package com.odontocare.dentists.model;

import com.odontocare.catalog.model.DentalService;
import com.odontocare.shared.model.VersionedEntity;
import com.odontocare.users.model.UserAccount;
import jakarta.persistence.*;
import java.util.*;

@Entity
@Table(name = "dentist")
public class Dentist extends VersionedEntity {
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "user_id", nullable = false, unique = true)
  private UserAccount user;

  @Column(name = "full_name", nullable = false, length = 120)
  private String fullName;

  @Column(name = "license_number", nullable = false, length = 40)
  private String licenseNumber;

  @Column(nullable = false, length = 120)
  private String specialty = "";

  @Column(nullable = false)
  private boolean active = true;

  @ManyToMany(fetch = FetchType.LAZY)
  @JoinTable(
      name = "dentist_service",
      joinColumns = @JoinColumn(name = "dentist_id"),
      inverseJoinColumns = @JoinColumn(name = "service_id"))
  private Set<DentalService> services = new HashSet<>();

  public UserAccount getUser() {
    return user;
  }

  public void setUser(UserAccount user) {
    this.user = user;
  }

  public String getFullName() {
    return fullName;
  }

  public void setFullName(String fullName) {
    this.fullName = fullName;
  }

  public String getLicenseNumber() {
    return licenseNumber;
  }

  public void setLicenseNumber(String licenseNumber) {
    this.licenseNumber = licenseNumber;
  }

  public String getSpecialty() {
    return specialty;
  }

  public void setSpecialty(String specialty) {
    this.specialty = specialty;
  }

  public boolean getActive() {
    return active;
  }

  public void setActive(boolean active) {
    this.active = active;
  }

  public Set<DentalService> getServices() {
    return services;
  }

  public void setServices(Set<DentalService> services) {
    this.services = services;
  }
}
