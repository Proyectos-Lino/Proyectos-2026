package com.odontocare.users.dto;

import com.odontocare.security.model.Permission;
import jakarta.validation.constraints.*;
import java.util.Set;

public record RoleRequest(
    @NotBlank @Size(max = 80) String name,
    @NotNull Set<@NotNull Permission> permissions,
    @NotNull @PositiveOrZero Long version) {}
