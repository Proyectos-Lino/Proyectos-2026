package com.odontocare.patients.dto;

import com.odontocare.patients.model.Patient;
import java.time.LocalDate;
import java.util.*;

public record PatientResponse(
    UUID id,
    String code,
    String fullName,
    String label,
    LocalDate birthDate,
    String documentType,
    String documentNumber,
    String address,
    String email,
    String emergencyName,
    String emergencyPhone,
    String notes,
    boolean provisional,
    boolean active,
    List<PatientRequest.ContactRequest> contacts,
    long version) {
  public static PatientResponse of(Patient patient) {
    return new PatientResponse(
        patient.getId(),
        patient.getCode(),
        patient.getFullName(),
        patient.getCode() + " · " + patient.getFullName(),
        patient.getBirthDate(),
        patient.getDocumentType(),
        patient.getDocumentNumber(),
        patient.getAddress(),
        patient.getEmail(),
        patient.getEmergencyName(),
        patient.getEmergencyPhone(),
        patient.getNotes(),
        patient.getProvisional(),
        patient.getActive(),
        patient.getContacts().stream()
            .map(
                contact ->
                    new PatientRequest.ContactRequest(
                        contact.getPhone(),
                        contact.getName(),
                        contact.getRelationship(),
                        contact.getGuardian(),
                        contact.getPayer()))
            .toList(),
        patient.getVersion());
  }
}
