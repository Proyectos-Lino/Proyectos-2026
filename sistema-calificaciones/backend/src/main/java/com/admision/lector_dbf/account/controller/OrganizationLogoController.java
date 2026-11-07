package com.admision.lector_dbf.account.controller;

import com.admision.lector_dbf.account.service.OrganizationLogoService;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/organizaciones/{organizationId}/logo")
public class OrganizationLogoController {
    private final OrganizationLogoService service;
    public OrganizationLogoController(OrganizationLogoService service) { this.service=service; }
    @GetMapping
    public ResponseEntity<byte[]> get(@PathVariable long organizationId,Authentication auth) {
        var logo=service.get(organizationId,auth);
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(logo.contentType())).cacheControl(CacheControl.noStore()).body(logo.content());
    }
    @PutMapping(consumes=MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Void> save(@PathVariable long organizationId,@RequestPart MultipartFile file,Authentication auth) {
        service.save(organizationId,file,auth); return ResponseEntity.noContent().build();
    }
    @DeleteMapping
    public ResponseEntity<Void> delete(@PathVariable long organizationId,Authentication auth) {
        service.delete(organizationId,auth); return ResponseEntity.noContent().build();
    }
}
