package com.odontocare.agent.service;

import java.util.Map;

public class ModelFailure extends RuntimeException {
  private final String code;
  private final long retryAfterSeconds;
  private final Map<String, Object> diagnostics;

  public ModelFailure(String code, String detail) {
    this(code, detail, 30);
  }

  public ModelFailure(String code, String detail, long retryAfterSeconds) {
    this(code, detail, retryAfterSeconds, Map.of());
  }

  public ModelFailure(
      String code, String detail, long retryAfterSeconds, Map<String, Object> diagnostics) {
    super(detail);
    this.code = code;
    this.retryAfterSeconds = Math.max(1, Math.min(86400, retryAfterSeconds));
    this.diagnostics = Map.copyOf(diagnostics);
  }

  public Map<String, Object> diagnostics() {
    return diagnostics;
  }

  public long retryAfterSeconds() {
    return retryAfterSeconds;
  }

  public String code() {
    return code;
  }
}
