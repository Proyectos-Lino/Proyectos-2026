package com.odontocare.patients.repository;

import com.odontocare.patients.model.Patient;
import java.util.UUID;
import org.springframework.data.jpa.repository.*;

public interface PatientRepository
    extends JpaRepository<Patient, UUID>, JpaSpecificationExecutor<Patient> {
  @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
  @org.springframework.data.jpa.repository.Query("select p from Patient p where p.id = :id")
  java.util.Optional<Patient> lockById(UUID id);

  boolean existsByCode(String code);

  boolean existsByDocumentTypeAndDocumentNumberAndIdNot(String type, String number, UUID id);
}
