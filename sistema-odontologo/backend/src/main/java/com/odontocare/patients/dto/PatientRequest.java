package com.odontocare.patients.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.LocalDate;
import java.util.List;

public record PatientRequest(
    Long version,
    @NotBlank @Size(max = 160) String fullName,
    LocalDate birthDate,
    @NotNull @Pattern(regexp = "|DNI|CE|PASSPORT|OTHER") String documentType,
    @NotNull @Size(max = 40) String documentNumber,
    @NotNull @Size(max = 250) String address,
    @NotNull @Email @Size(max = 160) String email,
    @NotNull @Size(max = 160) String emergencyName,
    @NotNull @Pattern(regexp = "|\\+[1-9][0-9]{7,14}") String emergencyPhone,
    @NotNull @Size(max = 2000) String notes,
    boolean provisional,
    boolean active,
    @NotNull @Size(min = 1, max = 8) List<@Valid ContactRequest> contacts) {
  public record ContactRequest(
      @NotBlank @Pattern(regexp = "\\+[1-9][0-9]{7,14}") String phone,
      @NotBlank @Size(max = 160) String name,
      @NotBlank @Size(max = 80) String relationship,
      boolean guardian,
      boolean payer) {}
}
