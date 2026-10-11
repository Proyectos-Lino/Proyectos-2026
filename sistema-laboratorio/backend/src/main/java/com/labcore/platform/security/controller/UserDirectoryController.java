package com.labcore.platform.security;

import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users")
public class UserDirectoryController {
  private final UserDirectoryService service;

  public UserDirectoryController(UserDirectoryService service) {
    this.service = service;
  }

  @GetMapping
  @PreAuthorize("hasRole('ADMIN')")
  public com.labcore.platform.common.web.PageResponse<UserDtos.UserSummary> list(
      @RequestParam(required = false) String search,
      @RequestParam(required = false) RoleCode role,
      @RequestParam(required = false) Boolean active,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    return com.labcore.platform.common.web.PageResponse.from(
        service.findPage(
            search,
            role,
            active,
            PageRequest.of(
                Math.max(0, page),
                Math.min(Math.max(1, size), 100),
                Sort.by(Sort.Direction.ASC, "fullName"))));
  }

  @GetMapping("/analysts")
  @PreAuthorize("hasAnyRole('ADMIN','TECHNICAL_MANAGER')")
  public List<UserDtos.UserSummary> analysts() {
    return service.analysts();
  }

  @GetMapping("/technical-managers")
  @PreAuthorize("hasAnyRole('ADMIN','TECHNICAL_MANAGER')")
  public List<UserDtos.UserSummary> technicalManagers() {
    return service.technicalManagers();
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  @PreAuthorize("hasRole('ADMIN')")
  public UserDtos.UserSummary create(@Valid @RequestBody UserDtos.CreateUserRequest request) {
    return service.create(request);
  }

  @PutMapping("/{userId}")
  @PreAuthorize("hasRole('ADMIN')")
  public UserDtos.UserSummary update(
      @PathVariable UUID userId, @Valid @RequestBody UserDtos.UpdateUserRequest request) {
    return service.update(userId, request);
  }

  @PatchMapping("/{userId}/status")
  @PreAuthorize("hasRole('ADMIN')")
  public UserDtos.UserSummary updateStatus(
      @PathVariable UUID userId,
      @Valid @RequestBody UserDtos.UpdateUserStatusRequest request,
      Authentication authentication) {
    return service.updateStatus(userId, request.active(), authentication.getName());
  }
}
