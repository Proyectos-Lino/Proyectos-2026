package com.odontocare.dentists.dto;

import java.util.UUID;

public record AssignedServiceResponse(UUID id, String name, boolean active) {}
