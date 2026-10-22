package com.odontocare.finance.controller;

import com.odontocare.finance.dto.PaymentContracts.*;
import com.odontocare.finance.service.ExpenseService;
import com.odontocare.shared.pagination.*;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/finance")
public class ExpenseController {
  private final ExpenseService service;

  public ExpenseController(ExpenseService service) {
    this.service = service;
  }

  @GetMapping("/expense-categories")
  @PreAuthorize("hasAuthority('FINANCES_READ')")
  public PageResponse<CategoryResponse> categories(
      @RequestParam(required = false) Boolean active, @Valid @ModelAttribute PageQuery query) {
    return service.categories(active, query);
  }

  @PostMapping("/expense-categories")
  @ResponseStatus(HttpStatus.CREATED)
  @PreAuthorize("hasAuthority('FINANCE_CONFIG_WRITE')")
  public CategoryResponse create(@Valid @RequestBody CategoryRequest r) {
    return service.saveCategory(null, r);
  }

  @PutMapping("/expense-categories/{id}")
  @PreAuthorize("hasAuthority('FINANCE_CONFIG_WRITE')")
  public CategoryResponse update(@PathVariable UUID id, @Valid @RequestBody CategoryRequest r) {
    return service.saveCategory(id, r);
  }

  @PostMapping("/expenses")
  @ResponseStatus(HttpStatus.CREATED)
  @PreAuthorize("hasAuthority('EXPENSES_WRITE')")
  public MovementResponse register(@Valid @RequestBody ExpenseRequest r) {
    return service.register(r);
  }

  @PostMapping("/expenses/{id}/reverse")
  @PreAuthorize("hasAuthority('FINANCES_ADJUST')")
  public MovementResponse reverse(@PathVariable UUID id, @Valid @RequestBody CorrectionRequest r) {
    return service.reverse(id, r);
  }
}
