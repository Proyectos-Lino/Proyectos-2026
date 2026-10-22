package com.odontocare.security.web;

import com.odontocare.shared.web.ApiProblems;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Component
public class SecurityProblemWriter {
  private final ObjectMapper mapper;

  public SecurityProblemWriter(ObjectMapper mapper) {
    this.mapper = mapper;
  }

  public void write(HttpServletResponse response, HttpStatus status, String detail)
      throws IOException {
    response.setStatus(status.value());
    response.setContentType("application/problem+json;charset=UTF-8");
    mapper.writeValue(
        response.getWriter(), ApiProblems.create(status, status.getReasonPhrase(), detail));
  }
}
