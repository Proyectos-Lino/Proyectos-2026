package com.odontocare.shared.web;

import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;

public final class ApiProblems {
  private ApiProblems() {}

  public static ProblemDetail create(HttpStatus status, String title, String message) {
    var detail = ProblemDetail.forStatusAndDetail(status, message);
    detail.setTitle(title);
    detail.setProperty("requestId", MDC.get("requestId"));
    return detail;
  }
}
