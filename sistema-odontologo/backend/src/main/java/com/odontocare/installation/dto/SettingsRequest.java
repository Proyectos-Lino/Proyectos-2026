package com.odontocare.installation.dto;

import jakarta.validation.constraints.*;

public record SettingsRequest(
    @NotBlank @Size(max = 120) String displayName,
    @NotBlank @Size(max = 60) String timeZone,
    @NotBlank @Pattern(regexp = "[A-Z]{3}") String currency,
    @NotNull @Size(max = 160) String legalName,
    @NotNull @Size(max = 250) String address,
    @NotNull @Pattern(regexp = "[+0-9() .-]{0,30}") String phone,
    @NotNull @Email @Size(max = 160) String email,
    @NotNull @Pattern(regexp = "#[0-9a-fA-F]{6}") String brandColor,
    @NotNull @Pattern(regexp = "#[0-9a-fA-F]{6}") String accentColor,
    @NotNull @Pattern(regexp = "DMY|MDY|YMD") String dateFormat,
    @NotNull @Size(max = 1000) String documentHeader,
    @NotNull @Size(max = 1000) String documentFooter,
    @NotNull @Size(max = 1000) String appointmentInstructions,
    @NotBlank @Pattern(regexp = "[A-Z0-9-]{1,10}") String patientPrefix,
    @NotNull @Min(1) @Max(999999999) Integer patientNextNumber,
    @NotBlank @Pattern(regexp = "[A-Z0-9-]{1,10}") String receiptPrefix,
    @NotNull @Min(1) @Max(999999999) Integer receiptNextNumber,
    @NotBlank @Pattern(regexp = "[A-Z0-9-]{1,10}") String budgetPrefix,
    @NotNull @Min(1) @Max(999999999) Integer budgetNextNumber,
    @NotNull @Min(0) @Max(43800) Integer minimumLeadMinutes,
    @NotNull @Min(0) @Max(120) Integer appointmentGapMinutes,
    @NotNull @PositiveOrZero Long version) {}
