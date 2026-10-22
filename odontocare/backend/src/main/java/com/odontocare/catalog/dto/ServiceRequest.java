package com.odontocare.catalog.dto;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.util.UUID;

public record ServiceRequest(
    @NotBlank @Size(max = 120) String name,
    @NotNull UUID categoryId,
    @NotNull @DecimalMin("0.00") @Digits(integer = 10, fraction = 2) BigDecimal price,
    @NotNull @Min(1) @Max(1440) Integer durationMinutes,
    @NotNull @Size(max = 1000) String description,
    @NotNull Boolean bookableByAgent,
    @NotNull Boolean active,
    @PositiveOrZero Long version) {}
