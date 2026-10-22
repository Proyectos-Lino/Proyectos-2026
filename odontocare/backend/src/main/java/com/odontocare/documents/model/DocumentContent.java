package com.odontocare.documents.model;

import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "document_content")
public class DocumentContent {
  @Id private UUID id;

  @Column(nullable = false, columnDefinition = "bytea")
  private byte[] content;

  public DocumentContent() {}

  public DocumentContent(UUID id, byte[] content) {
    this.id = id;
    this.content = content;
  }

  public byte[] getContent() {
    return content;
  }
}
