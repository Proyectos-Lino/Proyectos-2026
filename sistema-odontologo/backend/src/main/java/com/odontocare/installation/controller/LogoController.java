package com.odontocare.installation.controller;

import com.odontocare.installation.service.LogoService;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/system/logo")
public class LogoController {
  private final LogoService service;

  public LogoController(LogoService service) {
    this.service = service;
  }

  @GetMapping
  public ResponseEntity<byte[]> get() {
    var logo = service.getLogo();
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .contentType(MediaType.parseMediaType(logo.contentType()))
        .body(logo.content());
  }
}
