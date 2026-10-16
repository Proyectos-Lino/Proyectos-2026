package com.odontocare.patients.model;

import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "patient_contact")
public class PatientContact {
  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "patient_id", nullable = false)
  private Patient patient;

  @Column(nullable = false, length = 20)
  private String phone;

  @Column(nullable = false, length = 160)
  private String name;

  @Column(nullable = false, length = 80)
  private String relationship;

  @Column(nullable = false)
  private boolean guardian;

  @Column(nullable = false)
  private boolean payer;

  protected PatientContact() {}

  public PatientContact(
      Patient patient,
      String phone,
      String name,
      String relationship,
      boolean guardian,
      boolean payer) {
    this.patient = patient;
    this.phone = phone;
    this.name = name;
    this.relationship = relationship;
    this.guardian = guardian;
    this.payer = payer;
  }

  public UUID getId() {
    return id;
  }

  public String getPhone() {
    return phone;
  }

  public String getName() {
    return name;
  }

  public String getRelationship() {
    return relationship;
  }

  public boolean getGuardian() {
    return guardian;
  }

  public boolean getPayer() {
    return payer;
  }
}
