package com.odontocare.patients.dto;

import jakarta.validation.constraints.*;

public record QuickPatientRequest(
    @NotBlank @Size(max = 160) String fullName,
    @NotBlank @Pattern(regexp = "\\+[1-9][0-9]{7,14}") String phone) {}
