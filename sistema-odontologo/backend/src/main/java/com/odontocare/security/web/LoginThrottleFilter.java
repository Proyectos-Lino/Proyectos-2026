package com.odontocare.security.web;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import org.springframework.http.HttpStatus;
import org.springframework.web.filter.OncePerRequestFilter;

public class LoginThrottleFilter extends OncePerRequestFilter {
  private final LoginThrottle throttle;
  private final SecurityProblemWriter problems;

  public LoginThrottleFilter(LoginThrottle throttle, SecurityProblemWriter problems) {
    this.throttle = throttle;
    this.problems = problems;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    if (request.getMethod().equals("POST")
        && request.getServletPath().equals("/api/v1/auth/login")
        && !throttle.allow(request)) {
      response.setHeader("Retry-After", "300");
      problems.write(
          response,
          HttpStatus.TOO_MANY_REQUESTS,
          "Demasiados intentos. Espera cinco minutos antes de volver a intentarlo.");
      return;
    }
    chain.doFilter(request, response);
  }
}
