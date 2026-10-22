package com.odontocare.users.dto;

import java.util.Set;

public record RoleResponse(String code, String name, Set<String> permissions, long version) {}
