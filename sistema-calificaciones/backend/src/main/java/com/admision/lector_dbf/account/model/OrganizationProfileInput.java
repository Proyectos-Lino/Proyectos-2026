package com.admision.lector_dbf.account.model;

import jakarta.validation.constraints.*;

public record OrganizationProfileInput(@NotBlank @Size(max=180) String legalName,@Size(max=40) String taxId,
        @NotBlank @Size(max=250) String address,@NotBlank @Size(max=100) String city,@NotBlank @Size(max=100) String country,
        @Size(max=40) String phone,@NotBlank @Email @Size(max=254) String email,
        @Pattern(regexp="^(https?://[^\\s]+)?$",message="Usa una dirección web con http:// o https://") @Size(max=250) String website,
        @Size(max=180) String office,@Size(max=160) String signatoryName,@Size(max=160) String signatoryTitle) {}
