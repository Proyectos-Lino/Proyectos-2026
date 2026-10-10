package com.labcore.platform.security;

public class InvalidCredentialsException extends RuntimeException {
  public InvalidCredentialsException() {
    super("Las credenciales no son válidas.");
  }
}
