package com.admision.lector_dbf.account.model;

import java.util.List;
import jakarta.validation.constraints.*;
import jakarta.validation.Valid;

public final class AccountRequests {
    private AccountRequests() {}

    public record LoginRequest(@NotBlank @Email String email, @NotBlank String password) {}

    public record BootstrapRequest(@NotBlank String token, @NotBlank String organizationCode,
                                   @NotBlank String organizationName, @NotBlank @Email String email,
                                   @NotBlank String name, @NotBlank @Size(min = 8) String password,
                                   @NotNull @Valid OrganizationProfileInput profile) {}

    public record SessionView(long id, String email, String name, List<AccountModels.Organization> organizations) {}

    public record CreateUser(@NotBlank @Email String email, @NotBlank String name,
                             @NotBlank @Size(min = 8) String password, @NotBlank String role) {}

    public record LinkUser(@NotBlank @Email String email, @NotBlank String role) {}

    public record CreateOrganization(@NotNull Long currentOrganizationId, @NotBlank @Size(max=40) String code,
                                     @NotBlank @Size(max=180) String name,@NotNull @Valid OrganizationProfileInput profile) {}

    public record UpdateOrganization(@NotBlank @Size(max=180) String name,@NotNull @Valid OrganizationProfileInput profile) {}

    public record UpdateMembership(@NotBlank String role, boolean active) {}

    public record UpdateUser(@NotBlank String name, @NotBlank @Email String email,
                             @Size(min = 8) String password, @NotBlank String role,
                             boolean active) {}
}
