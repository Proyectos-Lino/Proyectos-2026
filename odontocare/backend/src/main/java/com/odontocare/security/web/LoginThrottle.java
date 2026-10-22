package com.odontocare.security.web;

import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

public class LoginThrottle {
  private static final int MAX_KEYS = 1000;
  private static final int MAX_ATTEMPTS = 10;
  private static final long WINDOW_SECONDS = 300;
  private final Map<String, Window> windows = new HashMap<>();

  private String key(HttpServletRequest request) {
    String username = request.getParameter("username");
    if (username != null) username = username.strip().toLowerCase(java.util.Locale.ROOT);
    return request.getRemoteAddr()
        + ":"
        + (username == null ? "" : username.substring(0, Math.min(60, username.length())));
  }

  public synchronized boolean allow(HttpServletRequest request) {
    long now = Instant.now().getEpochSecond();
    windows.entrySet().removeIf(entry -> entry.getValue().expiresAt <= now);
    String key = key(request);
    var window = windows.get(key);
    if (window == null) {
      if (windows.size() >= MAX_KEYS) return false;
      window = new Window(now + WINDOW_SECONDS);
      windows.put(key, window);
    }
    return ++window.attempts <= MAX_ATTEMPTS;
  }

  public synchronized void clear(HttpServletRequest request) {
    windows.remove(key(request));
  }

  private static final class Window {
    private final long expiresAt;
    private int attempts;

    private Window(long expiresAt) {
      this.expiresAt = expiresAt;
    }
  }
}
