package com.admision.lector_dbf.exam.model;

import jakarta.validation.constraints.NotBlank;

public record RecalculateRequest(@NotBlank String reason) {}
