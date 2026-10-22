package com.odontocare.shared.model;

import com.odontocare.shared.web.ApiException;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

@MappedSuperclass
public abstract class VersionedEntity {
  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Version private long version;

  @CreationTimestamp
  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @UpdateTimestamp
  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  public UUID getId() {
    return id;
  }

  public long getVersion() {
    return version;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }

  public void checkVersion(Long expected) {
    if (expected == null || expected != version) {
      throw ApiException.conflict(
          "El registro cambió desde que lo abriste. Actualiza la página y revisa los datos.");
    }
  }
}
