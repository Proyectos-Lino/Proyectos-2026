package com.labcore.platform.organization;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.util.UUID;

@Entity
@IdClass(OrganizationAuthorizedSigner.Key.class)
@Table(name = "organization_authorized_signer")
public class OrganizationAuthorizedSigner {
  @Id private UUID organizationId;
  @Id private UUID userId;
  private int displayOrder;

  protected OrganizationAuthorizedSigner() {}

  public OrganizationAuthorizedSigner(UUID organizationId, UUID userId, int displayOrder) {
    this.organizationId = organizationId;
    this.userId = userId;
    this.displayOrder = displayOrder;
  }

  public UUID getUserId() {
    return userId;
  }

  public int getDisplayOrder() {
    return displayOrder;
  }

  public record Key(UUID organizationId, UUID userId) implements Serializable {}
}
