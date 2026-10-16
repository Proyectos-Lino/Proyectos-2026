package com.odontocare.users.controller;

import com.odontocare.shared.pagination.*;
import com.odontocare.users.dto.*;
import com.odontocare.users.model.RoleCode;
import com.odontocare.users.service.UserService;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/users")
public class UserController {
  private final UserService service;

  public UserController(UserService service) {
    this.service = service;
  }

  @GetMapping
  @PreAuthorize("hasAuthority('USERS_READ')")
  public PageResponse<UserResponse> list(
      @Valid @ModelAttribute PageQuery query,
      @RequestParam(required = false) Boolean active,
      @RequestParam(required = false) RoleCode role) {
    return service.list(query, active, role);
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  @PreAuthorize("hasAuthority('USERS_WRITE')")
  public UserResponse create(@Valid @RequestBody UserRequest request) {
    return service.create(request);
  }

  @PutMapping("/{id}")
  @PreAuthorize("hasAuthority('USERS_WRITE')")
  public UserResponse update(@PathVariable UUID id, @Valid @RequestBody UserRequest request) {
    return service.update(id, request);
  }
}
