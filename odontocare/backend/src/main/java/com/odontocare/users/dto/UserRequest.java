package com.odontocare.users.dto;

import com.odontocare.users.model.RoleCode;
import jakarta.validation.constraints.*;
import java.util.Set;

public record UserRequest(
    @NotBlank @Pattern(regexp = "[a-z0-9._-]{3,60}") String username,
    @NotBlank @Size(max = 120) String displayName,
    @NotNull @Email @Size(max = 160) String email,
    @Size(max = 72) String password,
    @NotNull Boolean active,
    @NotEmpty @Size(max = 4) Set<@NotNull RoleCode> roles,
    @PositiveOrZero Long version) {}
