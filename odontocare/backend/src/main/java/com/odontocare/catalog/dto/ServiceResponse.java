package com.odontocare.catalog.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record ServiceResponse(
    UUID id,
    String name,
    UUID categoryId,
    String categoryName,
    BigDecimal price,
    int durationMinutes,
    String description,
    boolean bookableByAgent,
    boolean active,
    long version) {}
