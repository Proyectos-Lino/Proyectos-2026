package com.odontocare.dentists.dto;

import java.util.*;

public record DentistResponse(
    UUID id,
    UUID userId,
    String userName,
    String fullName,
    String licenseNumber,
    String specialty,
    boolean active,
    Map<UUID, String> services,
    long version) {}
