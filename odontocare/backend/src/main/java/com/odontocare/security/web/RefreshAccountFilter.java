package com.odontocare.security.web;

import com.odontocare.security.model.AccountPrincipal;
import com.odontocare.security.service.AccountAccessService;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.CannotCreateTransactionException;
import org.springframework.web.filter.OncePerRequestFilter;

public class RefreshAccountFilter extends OncePerRequestFilter {
  private final AccountAccessService access;
  private final SecurityProblemWriter problems;

  public RefreshAccountFilter(AccountAccessService access, SecurityProblemWriter problems) {
    this.access = access;
    this.problems = problems;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    var authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication != null
        && authentication.getPrincipal() instanceof AccountPrincipal previous) {
      AccountPrincipal current;
      try {
        current = access.refresh(previous.getId());
      } catch (DataAccessException | CannotCreateTransactionException exception) {
        problems.write(
            response,
            HttpStatus.SERVICE_UNAVAILABLE,
            "No pudimos validar la sesión. Intenta nuevamente.");
        return;
      }
      if (current == null || current.getAuthVersion() != previous.getAuthVersion()) {
        SecurityContextHolder.clearContext();
        var session = request.getSession(false);
        if (session != null) session.invalidate();
        problems.write(
            response,
            HttpStatus.UNAUTHORIZED,
            "La sesión dejó de ser válida. Inicia sesión nuevamente.");
        return;
      }
      var refreshed =
          UsernamePasswordAuthenticationToken.authenticated(
              current, null, current.getAuthorities());
      refreshed.setDetails(authentication.getDetails());
      SecurityContextHolder.getContext().setAuthentication(refreshed);
    }
    chain.doFilter(request, response);
  }
}
