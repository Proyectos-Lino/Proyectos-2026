package com.odontocare.documents.model;

import jakarta.persistence.*;

@Entity
@Table(name = "document_policy")
public class DocumentPolicy {
  @Id private Short id = 1;
  @Version private long version;

  @Column(name = "max_file_mi_b")
  private Integer maxFileMiB = 20;

  public Integer getMaxFileMiB() {
    return maxFileMiB;
  }

  public void setMaxFileMiB(Integer value) {
    maxFileMiB = value;
  }

  public long getVersion() {
    return version;
  }
}
