package com.labcore.platform.organization;

import com.labcore.platform.common.BaseEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "organization_contact")
public class OrganizationContact extends BaseEntity {
  @Id private UUID id = UUID.randomUUID();
  private UUID organizationId;

  @Enumerated(EnumType.STRING)
  private OrganizationContactType contactType;

  private String label;
  private String value;
  private int displayOrder;

  protected OrganizationContact() {}

  public OrganizationContact(
      UUID organizationId,
      OrganizationContactType contactType,
      String label,
      String value,
      int displayOrder) {
    this.organizationId = organizationId;
    this.contactType = contactType;
    this.label = label;
    this.value = value;
    this.displayOrder = displayOrder;
  }

  public UUID getId() {
    return id;
  }

  public OrganizationContactType getContactType() {
    return contactType;
  }

  public String getLabel() {
    return label;
  }

  public String getValue() {
    return value;
  }

  public int getDisplayOrder() {
    return displayOrder;
  }
}
