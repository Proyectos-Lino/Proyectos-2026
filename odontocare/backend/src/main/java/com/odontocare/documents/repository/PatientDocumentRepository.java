package com.odontocare.documents.repository;

import com.odontocare.documents.model.PatientDocument;
import java.util.*;
import org.springframework.data.jpa.repository.*;

public interface PatientDocumentRepository
    extends JpaRepository<PatientDocument, UUID>, JpaSpecificationExecutor<PatientDocument> {
  @org.springframework.data.jpa.repository.Query(
      "select coalesce(sum(d.byteSize),0) from PatientDocument d")
  long totalBytes();
}
