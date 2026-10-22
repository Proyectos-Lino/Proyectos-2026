package com.odontocare.security.config;

import java.net.URI;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
public class FrontendCorsConfiguration {
  @Bean
  UrlBasedCorsConfigurationSource corsConfigurationSource(
      @Value("${odontocare.frontend-url:http://127.0.0.1:5173}") String frontendUrl) {
    String origin = frontendUrl.strip().replaceAll("/+$", "");
    URI uri = URI.create(origin);
    if (!List.of("https", "http").contains(uri.getScheme())
        || uri.getHost() == null
        || uri.getUserInfo() != null
        || !uri.getPath().isEmpty()
        || uri.getQuery() != null
        || uri.getFragment() != null
        || uri.getPort() > 65535
        || ("http".equals(uri.getScheme())
            && !List.of("localhost", "127.0.0.1").contains(uri.getHost()))) {
      throw new IllegalArgumentException(
          "FRONTEND_URL debe ser el origen HTTPS público del frontend.");
    }
    var cors = new CorsConfiguration();
    cors.setAllowedOrigins(List.of(origin));
    cors.setAllowCredentials(true);
    cors.setAllowedMethods(List.of("GET", "HEAD", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
    cors.setAllowedHeaders(List.of("Content-Type", "Accept", "X-CSRF-TOKEN", "X-Request-ID"));
    cors.setExposedHeaders(List.of("X-Request-ID", "X-Document-Pages", "Content-Disposition"));
    cors.setMaxAge(3600L);
    var source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/api/**", cors);
    return source;
  }
}
