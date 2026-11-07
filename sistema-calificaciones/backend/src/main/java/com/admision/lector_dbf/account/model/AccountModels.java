package com.admision.lector_dbf.account.model;



public final class AccountModels {
    private AccountModels() {}

    public record User(long id, String email, String passwordHash, String name, boolean active) {}

    public record Membership(long organizationId, long userId, String role, boolean active) {}

    public record UserView(long id, String email, String name, boolean active, String role,
                           boolean membershipActive, boolean sharedAccount) {}

    public record Organization(long id, String code, String name, String role) {}
}
