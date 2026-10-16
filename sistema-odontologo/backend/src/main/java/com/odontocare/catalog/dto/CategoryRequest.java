package com.odontocare.catalog.dto;

import jakarta.validation.constraints.*;

public record CategoryRequest(
    @NotBlank @Size(max = 100) String name,
    @NotNull Boolean active,
    @PositiveOrZero Long version) {}
