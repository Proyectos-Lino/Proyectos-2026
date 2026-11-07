package com.admision.lector_dbf.account.model;

public record OrganizationProfile(long id,String name,String code,String legalName,String taxId,String address,
                                   String city,String country,String phone,String email,String website,String office,
                                   String signatoryName,String signatoryTitle,boolean hasLogo) {}
