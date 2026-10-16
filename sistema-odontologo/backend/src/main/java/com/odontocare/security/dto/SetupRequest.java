package com.odontocare.security.dto;

import jakarta.validation.constraints.*;

public record SetupRequest(
    @NotBlank @Pattern(regexp = "[a-z0-9._-]{3,60}") String username,
    @NotBlank @Size(max = 120) String displayName,
    @NotBlank @Size(max = 72) String password) {}
