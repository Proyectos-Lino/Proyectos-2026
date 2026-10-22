package com.odontocare.installation.controller;

import com.odontocare.installation.dto.*;
import com.odontocare.installation.service.*;
import jakarta.validation.Valid;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/settings")
public class SettingsController {
  private final InstallationService installation;
  private final LogoService logos;

  public SettingsController(InstallationService installation, LogoService logos) {
    this.installation = installation;
    this.logos = logos;
  }

  @GetMapping
  @PreAuthorize("hasAuthority('SETTINGS_READ')")
  public SettingsResponse get() {
    return installation.getSettings();
  }

  @PutMapping
  @PreAuthorize("hasAuthority('SETTINGS_WRITE')")
  public SettingsResponse update(@Valid @RequestBody SettingsRequest request) {
    return installation.update(request);
  }

  @PostMapping(value = "/logo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @PreAuthorize("hasAuthority('SETTINGS_WRITE')")
  public void uploadLogo(@RequestParam MultipartFile file, @RequestParam long version) {
    logos.upload(file, version);
  }

  @DeleteMapping("/logo")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @PreAuthorize("hasAuthority('SETTINGS_WRITE')")
  public void removeLogo(@RequestParam long version) {
    logos.remove(version);
  }
}
