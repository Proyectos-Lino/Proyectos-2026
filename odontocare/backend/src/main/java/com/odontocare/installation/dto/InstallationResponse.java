package com.odontocare.installation.dto;

public record InstallationResponse(
    String displayName,
    String timeZone,
    String currency,
    String brandColor,
    String accentColor,
    String dateFormat,
    boolean hasLogo,
    int logoRevision) {}
