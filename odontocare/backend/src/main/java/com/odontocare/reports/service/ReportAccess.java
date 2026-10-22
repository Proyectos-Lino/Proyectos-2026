package com.odontocare.reports.service;

import com.odontocare.reports.model.ReportType;
import com.odontocare.shared.web.ApiException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component
public class ReportAccess {
  public boolean can(String permission) {
    var auth = SecurityContextHolder.getContext().getAuthentication();
    return auth != null
        && auth.isAuthenticated()
        && auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals(permission));
  }

  public void require(ReportType type, boolean export) {
    if (!can("REPORTS_READ") || !can(type.permission()) || (export && !can("REPORTS_EXPORT")))
      throw ApiException.forbidden();
  }
}
