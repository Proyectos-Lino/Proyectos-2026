package com.odontocare.finance.model;

import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "financial_content")
public class FinancialContent {
  @Id private UUID id;

  @Column(columnDefinition = "bytea", nullable = false)
  private byte[] content;

  public UUID getId() {
    return id;
  }

  public void setId(UUID v) {
    id = v;
  }

  public byte[] getContent() {
    return content;
  }

  public void setContent(byte[] v) {
    content = v;
  }
}
