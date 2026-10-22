package com.odontocare.appointments.dto;

import com.odontocare.appointments.model.AppointmentStatus;
import jakarta.validation.constraints.*;

public record StatusRequest(
    @NotNull Long version,
    @NotNull AppointmentStatus status,
    @NotNull @Size(max = 500) String reason) {}
