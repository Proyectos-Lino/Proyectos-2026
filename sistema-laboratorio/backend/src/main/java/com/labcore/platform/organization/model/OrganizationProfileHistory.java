package com.labcore.platform.organization;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "organization_profile_history")
public class OrganizationProfileHistory {
  @Id private UUID id = UUID.randomUUID();
  private UUID organizationId;
  private String action;
  private String changeNote;
  private String performedBy;
  private Instant occurredAt;

  protected OrganizationProfileHistory() {}

  public OrganizationProfileHistory(UUID organizationId, String performedBy) {
    this.organizationId = organizationId;
    this.action = "PROFILE_UPDATED";
    this.changeNote = "Identidad, contactos o configuración documental actualizados.";
    this.performedBy = performedBy;
    this.occurredAt = Instant.now();
  }
}
