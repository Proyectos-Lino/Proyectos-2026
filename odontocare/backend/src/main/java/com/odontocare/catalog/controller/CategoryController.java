package com.odontocare.catalog.controller;

import com.odontocare.catalog.dto.*;
import com.odontocare.catalog.service.CategoryService;
import com.odontocare.shared.pagination.*;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/categories")
public class CategoryController {
  private final CategoryService service;

  public CategoryController(CategoryService service) {
    this.service = service;
  }

  @GetMapping
  @PreAuthorize("hasAuthority('SERVICES_READ')")
  public PageResponse<CategoryResponse> list(
      @Valid @ModelAttribute PageQuery query, @RequestParam(required = false) Boolean active) {
    return service.list(query, active);
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  @PreAuthorize("hasAuthority('SERVICES_WRITE')")
  public CategoryResponse create(@Valid @RequestBody CategoryRequest request) {
    return service.create(request);
  }

  @PutMapping("/{id}")
  @PreAuthorize("hasAuthority('SERVICES_WRITE')")
  public CategoryResponse update(
      @PathVariable UUID id, @Valid @RequestBody CategoryRequest request) {
    return service.update(id, request);
  }
}
