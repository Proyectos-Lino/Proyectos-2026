package com.odontocare.finance.controller;

import com.odontocare.finance.dto.PaymentContracts.*;
import com.odontocare.finance.service.CashService;
import com.odontocare.shared.pagination.*;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/finance/cash")
public class CashController {
  private final CashService service;

  public CashController(CashService service) {
    this.service = service;
  }

  @GetMapping("/{id}/report")
  @PreAuthorize("hasAuthority('CASH_READ')")
  public org.springframework.http.ResponseEntity<byte[]> report(@PathVariable UUID id) {
    var d = service.report(id);
    return org.springframework.http.ResponseEntity.ok()
        .contentType(org.springframework.http.MediaType.APPLICATION_PDF)
        .header(
            "Content-Disposition",
            org.springframework.http.ContentDisposition.attachment()
                .filename(d.fileName())
                .build()
                .toString())
        .header("Cache-Control", "no-store")
        .body(d.bytes());
  }

  @GetMapping("/current")
  @PreAuthorize("hasAuthority('CASH_READ')")
  public CashResponse current() {
    return service.current();
  }

  @GetMapping
  @PreAuthorize("hasAuthority('CASH_READ')")
  public PageResponse<CashResponse> list(
      @RequestParam(required = false) Boolean closed, @Valid @ModelAttribute PageQuery query) {
    return service.list(closed, query);
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  @PreAuthorize("hasAuthority('CASH_WRITE')")
  public CashResponse open(@Valid @RequestBody OpenCashRequest r) {
    return service.open(r);
  }

  @PostMapping("/{id}/close")
  @PreAuthorize("hasAuthority('CASH_WRITE')")
  public CashResponse close(@PathVariable UUID id, @Valid @RequestBody CloseCashRequest r) {
    return service.close(id, r);
  }
}
