package com.labcore.platform.common.exception;

import java.util.UUID;

public class ResourceNotFoundException extends RuntimeException {

  public ResourceNotFoundException(String resource, UUID id) {
    super("No se encontró %s con id %s.".formatted(resource, id));
  }
}
