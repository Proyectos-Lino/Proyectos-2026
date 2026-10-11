package com.labcore.platform.common.exception;

import com.labcore.platform.security.InvalidCredentialsException;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.util.Map;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {
  private static final Logger LOGGER = LoggerFactory.getLogger(GlobalExceptionHandler.class);

  @ExceptionHandler(ResourceNotFoundException.class)
  ResponseEntity<ProblemDetail> handleNotFound(
      ResourceNotFoundException exception, HttpServletRequest request) {
    return problem(
        HttpStatus.NOT_FOUND,
        "Recurso no encontrado",
        exception.getMessage(),
        request.getRequestURI());
  }

  @ExceptionHandler(BusinessRuleViolationException.class)
  ResponseEntity<ProblemDetail> handleBusinessRule(
      BusinessRuleViolationException exception, HttpServletRequest request) {
    return problem(
        HttpStatus.UNPROCESSABLE_ENTITY,
        "No se puede completar la operación",
        exception.getMessage(),
        request.getRequestURI());
  }

  @ExceptionHandler(InvalidCredentialsException.class)
  ProblemDetail invalidCredentials(InvalidCredentialsException exception) {
    ProblemDetail problem =
        ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, exception.getMessage());
    problem.setTitle("No autorizado");
    return problem;
  }

  @ExceptionHandler(AccessDeniedException.class)
  ResponseEntity<ProblemDetail> handleAccessDenied(
      AccessDeniedException exception, HttpServletRequest request) {
    LOGGER.warn("Access denied for {} {}", request.getMethod(), request.getRequestURI());
    return problem(
        HttpStatus.FORBIDDEN,
        "Acceso denegado",
        "Tu usuario no cuenta con permisos para realizar esta operación.",
        request.getRequestURI());
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  ResponseEntity<ProblemDetail> handleValidation(
      MethodArgumentNotValidException exception, HttpServletRequest request) {
    Map<String, String> errors =
        exception.getBindingResult().getFieldErrors().stream()
            .collect(
                Collectors.toMap(
                    FieldError::getField,
                    FieldError::getDefaultMessage,
                    (first, ignored) -> first));
    ProblemDetail detail =
        ProblemDetail.forStatusAndDetail(
            HttpStatus.BAD_REQUEST, "Los datos enviados no son válidos.");
    detail.setTitle("Error de validación");
    detail.setInstance(URI.create(request.getRequestURI()));
    detail.setProperty("errors", errors);
    return ResponseEntity.badRequest().body(detail);
  }

  @ExceptionHandler(Exception.class)
  ResponseEntity<ProblemDetail> handleUnexpected(Exception exception, HttpServletRequest request) {
    LOGGER.error(
        "Unexpected error while processing {} {}",
        request.getMethod(),
        request.getRequestURI(),
        exception);
    ProblemDetail detail =
        ProblemDetail.forStatusAndDetail(
            HttpStatus.INTERNAL_SERVER_ERROR, "Ocurrió un error inesperado.");
    detail.setTitle("Error interno");
    detail.setInstance(URI.create(request.getRequestURI()));
    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(detail);
  }

  private ResponseEntity<ProblemDetail> problem(
      HttpStatus status, String title, String message, String path) {
    ProblemDetail detail = ProblemDetail.forStatusAndDetail(status, message);
    detail.setTitle(title);
    detail.setInstance(URI.create(path));
    return ResponseEntity.status(status).body(detail);
  }
}
