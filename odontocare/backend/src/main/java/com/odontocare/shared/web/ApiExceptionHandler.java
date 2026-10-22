package com.odontocare.shared.web;

import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.*;
import org.springframework.http.*;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.transaction.CannotCreateTransactionException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@RestControllerAdvice
public class ApiExceptionHandler {
  private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

  @ExceptionHandler(ApiException.class)
  public ProblemDetail business(ApiException exception) {
    return ApiProblems.create(exception.getStatus(), "Revisa la solicitud", exception.getMessage());
  }

  @ExceptionHandler({
    DataIntegrityViolationException.class,
    ObjectOptimisticLockingFailureException.class
  })
  public ProblemDetail conflict(Exception exception) {
    return ApiProblems.create(
        HttpStatus.CONFLICT,
        "No pudimos guardar",
        "El registro está duplicado, se superpone con otro o cambió desde que lo abriste. Actualiza"
            + " y revisa los datos.");
  }

  @ExceptionHandler({DataAccessException.class, CannotCreateTransactionException.class})
  public ProblemDetail database(Exception exception) {
    log.warn("Database operation failed: {}", exception.getClass().getSimpleName());
    return ApiProblems.create(
        HttpStatus.SERVICE_UNAVAILABLE,
        "Servicio temporalmente no disponible",
        "No pudimos acceder a la información. Intenta nuevamente en unos momentos.");
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ProblemDetail validation(MethodArgumentNotValidException exception) {
    var detail =
        ApiProblems.create(
            HttpStatus.BAD_REQUEST, "Datos inválidos", "Revisa los campos indicados.");
    List<FieldErrorResponse> errors =
        exception.getBindingResult().getFieldErrors().stream()
            .map(error -> new FieldErrorResponse(error.getField(), error.getDefaultMessage()))
            .toList();
    detail.setProperty("errors", errors);
    return detail;
  }

  @ExceptionHandler({
    MethodArgumentTypeMismatchException.class,
    HttpMessageNotReadableException.class,
    HandlerMethodValidationException.class
  })
  public ProblemDetail malformed(Exception exception) {
    return ApiProblems.create(
        HttpStatus.BAD_REQUEST,
        "Datos inválidos",
        "Revisa formatos, valores y parámetros de la solicitud.");
  }

  @ExceptionHandler(AccessDeniedException.class)
  public ProblemDetail denied(AccessDeniedException exception) {
    return ApiProblems.create(
        HttpStatus.FORBIDDEN, "Acceso denegado", "No tienes permiso para esta operación.");
  }

  @ExceptionHandler(NoResourceFoundException.class)
  public ProblemDetail missing(NoResourceFoundException exception) {
    return ApiProblems.create(
        HttpStatus.NOT_FOUND, "No encontrado", "La dirección solicitada no existe.");
  }

  @ExceptionHandler(MaxUploadSizeExceededException.class)
  public ProblemDetail upload(MaxUploadSizeExceededException exception) {
    return ApiProblems.create(
        HttpStatus.PAYLOAD_TOO_LARGE,
        "Archivo demasiado grande",
        "El archivo excede el límite de la instalación.");
  }

  @ExceptionHandler(org.springframework.web.HttpRequestMethodNotSupportedException.class)
  public ProblemDetail methodNotAllowed(Exception exception) {
    return ApiProblems.create(
        HttpStatus.METHOD_NOT_ALLOWED,
        "Método no permitido",
        "Esta dirección no admite la operación solicitada.");
  }

  @ExceptionHandler(org.springframework.web.HttpMediaTypeNotSupportedException.class)
  public ProblemDetail unsupportedMedia(Exception exception) {
    return ApiProblems.create(
        HttpStatus.UNSUPPORTED_MEDIA_TYPE,
        "Formato no compatible",
        "Utiliza el formato requerido para esta operación.");
  }

  @ExceptionHandler(Exception.class)
  public ProblemDetail unexpected(Exception exception) {
    log.error("Unexpected request failure: {}", exception.getClass().getSimpleName());
    return ApiProblems.create(
        HttpStatus.INTERNAL_SERVER_ERROR,
        "No pudimos completar la solicitud",
        "Intenta nuevamente. Si continúa, comunica el código de referencia al soporte.");
  }

  private record FieldErrorResponse(String field, String message) {}
}
