package com.odontocare.security.config;

import com.odontocare.security.dto.SessionResponse;
import com.odontocare.security.model.AccountPrincipal;
import com.odontocare.security.service.*;
import com.odontocare.security.web.*;
import jakarta.servlet.DispatcherType;
import org.springframework.context.annotation.*;
import org.springframework.http.*;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import tools.jackson.databind.ObjectMapper;

@Configuration
@EnableMethodSecurity
public class SecurityConfiguration {
  @Bean
  PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder(12);
  }

  @Bean
  SecurityFilterChain securityFilterChain(
      HttpSecurity http,
      AccountAccessService access,
      AuthenticationAuditService audit,
      SecurityProblemWriter problems,
      ObjectMapper mapper)
      throws Exception {
    var throttle = new LoginThrottle();
    http.cors(cors -> {});
    http.authorizeHttpRequests(
        authorize ->
            authorize
                .dispatcherTypeMatchers(DispatcherType.ERROR)
                .permitAll()
                .requestMatchers(
                    HttpMethod.GET,
                    "/api/v1/system/installation",
                    "/api/v1/system/logo",
                    "/api/v1/auth/session",
                    "/api/v1/auth/csrf",
                    "/actuator/health")
                .permitAll()
                .requestMatchers(HttpMethod.POST, "/api/v1/auth/setup", "/api/v1/auth/login")
                .permitAll()
                .requestMatchers(
                    HttpMethod.POST,
                    "/api/v1/integrations/whatsapp/inbound",
                    "/api/v1/integrations/whatsapp/status",
                    "/api/v1/integrations/kapso/events")
                .permitAll()
                .requestMatchers("/api/v1/**")
                .authenticated()
                .anyRequest()
                .denyAll());
    http.csrf(
        csrf ->
            csrf.ignoringRequestMatchers(
                org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher
                    .pathPattern(HttpMethod.POST, "/api/v1/integrations/whatsapp/inbound"),
                org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher
                    .pathPattern(HttpMethod.POST, "/api/v1/integrations/whatsapp/status"),
                org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher
                    .pathPattern(HttpMethod.POST, "/api/v1/integrations/kapso/events")));
    http.exceptionHandling(
        errors ->
            errors
                .authenticationEntryPoint(
                    (request, response, exception) ->
                        problems.write(
                            response,
                            HttpStatus.UNAUTHORIZED,
                            "Inicia sesión para acceder al sistema."))
                .accessDeniedHandler(
                    (request, response, exception) ->
                        problems.write(
                            response,
                            HttpStatus.FORBIDDEN,
                            "No tienes permiso o la protección de la solicitud caducó. Actualiza e"
                                + " intenta nuevamente.")));
    http.formLogin(
        form ->
            form.loginProcessingUrl("/api/v1/auth/login")
                .successHandler(
                    (request, response, authentication) -> {
                      var actor = (AccountPrincipal) authentication.getPrincipal();
                      throttle.clear(request);
                      audit.login(actor);
                      response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                      mapper.writeValue(response.getWriter(), SessionResponse.of(false, actor));
                    })
                .failureHandler(
                    (request, response, exception) -> {
                      audit.failure();
                      problems.write(
                          response,
                          HttpStatus.UNAUTHORIZED,
                          "Usuario o contraseña incorrectos, o cuenta no disponible.");
                    }));
    http.logout(
        logout ->
            logout
                .logoutUrl("/api/v1/auth/logout")
                .addLogoutHandler(
                    (request, response, authentication) -> {
                      if (authentication != null
                          && authentication.getPrincipal() instanceof AccountPrincipal actor)
                        audit.logout(actor);
                    })
                .deleteCookies("JSESSIONID")
                .logoutSuccessHandler(
                    (request, response, authentication) ->
                        response.setStatus(HttpStatus.NO_CONTENT.value())));
    http.addFilterBefore(
        new LoginThrottleFilter(throttle, problems), UsernamePasswordAuthenticationFilter.class);
    http.addFilterBefore(new RefreshAccountFilter(access, problems), AuthorizationFilter.class);
    return http.build();
  }
}
