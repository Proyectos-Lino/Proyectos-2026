package com.odontocare.users.dto;

import java.util.*;

public record UserResponse(
    UUID id,
    String username,
    String displayName,
    String email,
    boolean active,
    Set<String> roles,
    long version) {}
