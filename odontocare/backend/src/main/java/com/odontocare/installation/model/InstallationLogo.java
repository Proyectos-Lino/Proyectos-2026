package com.odontocare.installation.model;

import jakarta.persistence.*;

@Entity
@Table(name = "installation_logo")
public class InstallationLogo {
  @Id private Short id = 1;

  @Column(name = "content_type", nullable = false, length = 30)
  private String contentType;

  @Column(nullable = false, columnDefinition = "bytea")
  private byte[] content;

  public String getContentType() {
    return contentType;
  }

  public void setContentType(String contentType) {
    this.contentType = contentType;
  }

  public byte[] getContent() {
    return content;
  }

  public void setContent(byte[] content) {
    this.content = content;
  }
}
