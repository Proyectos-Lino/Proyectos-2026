package com.labcore.platform.workflow;

import com.labcore.platform.common.web.PageResponse;
import com.labcore.platform.orders.ServiceOrderService;
import com.labcore.platform.results.ResultService;
import com.labcore.platform.security.RoleCode;
import com.labcore.platform.workorders.*;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/operation-versions")
@PreAuthorize("hasAnyRole('ADMIN','COMMERCIAL','TECHNICAL_MANAGER','ANALYST','VALIDATOR')")
public class OperationVersionController {
  private final OperationVersionService versions;
  private final CorrectionAuthority authority;
  private final WorkOrderService workOrders;
  private final ServiceOrderService orders;
  private final ResultService results;

  public OperationVersionController(
      OperationVersionService versions,
      CorrectionAuthority authority,
      WorkOrderService workOrders,
      ServiceOrderService orders,
      ResultService results) {
    this.versions = versions;
    this.authority = authority;
    this.workOrders = workOrders;
    this.orders = orders;
    this.results = results;
  }

  @GetMapping("/{type}/{id}")
  public PageResponse<OperationVersionDtos.Version> list(
      @PathVariable OperationType type,
      @PathVariable UUID id,
      @RequestParam(defaultValue = "0") int page,
      Authentication authentication) {
    var actor = authority.user(authentication.getName());
    switch (type) {
      case SERVICE_ORDER -> {
        if (!authority.hasRole(actor, RoleCode.ADMIN)
            && !authority.hasRole(actor, RoleCode.COMMERCIAL))
          throw new AccessDeniedException("La historia comercial requiere permiso comercial.");
        orders.getRequired(id);
      }
      case WORK_ORDER ->
          authority.requireManager(workOrders.getRequired(id), authentication.getName());
      case RESULT -> {
        if (!authority.hasRole(actor, RoleCode.ADMIN)
            && !authority.hasRole(actor, RoleCode.TECHNICAL_MANAGER)
            && !authority.hasRole(actor, RoleCode.VALIDATOR)
            && !authority.hasRole(actor, RoleCode.ANALYST))
          throw new AccessDeniedException("Sin permiso técnico.");
        results.getByWorkOrderTest(id, authentication.getName());
      }
    }
    return PageResponse.from(versions.find(type, id, PageRequest.of(Math.max(0, page), 5)));
  }
}
