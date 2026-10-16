package com.odontocare.dentists.dto;

import jakarta.validation.constraints.*;
import java.util.*;

public record DentistRequest(
    @NotNull UUID userId,
    @NotBlank @Size(max = 120) String fullName,
    @NotBlank @Size(max = 40) String licenseNumber,
    @NotNull @Size(max = 120) String specialty,
    @NotNull Boolean active,
    @NotNull @Size(max = 100) Set<@NotNull UUID> serviceIds,
    @PositiveOrZero Long version) {}
