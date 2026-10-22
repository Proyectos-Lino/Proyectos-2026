package com.odontocare.users.controller;

import com.odontocare.security.model.Permission;
import com.odontocare.shared.pagination.*;
import com.odontocare.users.dto.*;
import com.odontocare.users.service.RoleService;
import jakarta.validation.Valid;
import java.util.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/roles")
public class RoleController {
  private final RoleService service;

  public RoleController(RoleService service) {
    this.service = service;
  }

  @GetMapping
  @PreAuthorize("hasAuthority('ROLES_READ')")
  public PageResponse<RoleResponse> list(@Valid @ModelAttribute PageQuery query) {
    return service.list(query);
  }

  @PutMapping("/{code}")
  @PreAuthorize("hasAuthority('ROLES_WRITE') and hasRole('ADMIN')")
  public RoleResponse update(@PathVariable String code, @Valid @RequestBody RoleRequest request) {
    return service.update(code, request);
  }

  @GetMapping("/permissions")
  @PreAuthorize("hasAuthority('ROLES_READ')")
  public List<String> permissions() {
    return EnumSet.allOf(Permission.class).stream().map(Enum::name).toList();
  }
}
