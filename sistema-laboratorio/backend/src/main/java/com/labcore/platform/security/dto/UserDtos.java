package com.labcore.platform.security;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public final class UserDtos {
  private UserDtos() {}

  public record CreateUserRequest(
      @NotBlank @Size(max = 160) String fullName,
      @NotBlank @Email String email,
      @NotBlank @Size(min = 12, max = 100) String password,
      @NotEmpty Set<RoleCode> roles,
      UUID laboratoryAreaId) {}

  public record UpdateUserRequest(
      @NotBlank @Size(max = 160) String fullName,
      @NotBlank @Email String email,
      @Size(min = 12, max = 100) String password,
      @NotEmpty Set<RoleCode> roles,
      UUID laboratoryAreaId) {}

  public record UpdateUserStatusRequest(@NotNull Boolean active) {}

  public record UserSummary(
      UUID id,
      String fullName,
      String email,
      boolean active,
      List<RoleCode> roles,
      UUID laboratoryAreaId,
      String laboratoryAreaName) {}
}
