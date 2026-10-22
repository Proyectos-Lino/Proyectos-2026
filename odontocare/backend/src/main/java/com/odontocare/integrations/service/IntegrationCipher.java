package com.odontocare.integrations.service;

import java.nio.file.*;
import java.security.SecureRandom;
import java.util.*;
import javax.crypto.Cipher;
import javax.crypto.spec.*;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

@Component
public class IntegrationCipher {
  private final Environment environment;
  private byte[] localKey;

  public IntegrationCipher(Environment environment) {
    this.environment = environment;
  }

  private synchronized byte[] key() throws Exception {
    String configured =
        environment.getProperty("odontocare.integrations.encryption-key", "").strip();
    if (!configured.isBlank()) {
      byte[] key = Base64.getDecoder().decode(configured);
      if (key.length != 32) throw new IllegalArgumentException("Invalid key length");
      return key;
    }
    String[] profiles =
        environment.getActiveProfiles().length == 0
            ? environment.getDefaultProfiles()
            : environment.getActiveProfiles();
    if (!Arrays.asList(profiles).contains("local"))
      throw new IllegalStateException("Encryption key unavailable");
    if (localKey == null) {
      Path path = Path.of("config", "integrations.master.key");
      Files.createDirectories(path.getParent());
      if (!Files.exists(path)) {
        byte[] fresh = new byte[32];
        new SecureRandom().nextBytes(fresh);
        try {
          Files.writeString(
              path, Base64.getEncoder().encodeToString(fresh), StandardOpenOption.CREATE_NEW);
        } catch (FileAlreadyExistsException ignored) {
          /* Another startup created it. */
        }
      }
      localKey = Base64.getDecoder().decode(Files.readString(path).strip());
      if (localKey.length != 32) throw new IllegalStateException("Invalid local key");
    }
    return localKey.clone();
  }

  public boolean available() {
    try {
      key();
      return true;
    } catch (Exception failure) {
      return false;
    }
  }

  public byte[] encrypt(byte[] plain) {
    try {
      byte[] nonce = new byte[12];
      new SecureRandom().nextBytes(nonce);
      Cipher cipher = cipher(Cipher.ENCRYPT_MODE, nonce);
      byte[] data = cipher.doFinal(plain), result = new byte[nonce.length + data.length];
      System.arraycopy(nonce, 0, result, 0, nonce.length);
      System.arraycopy(data, 0, result, nonce.length, data.length);
      return result;
    } catch (Exception failure) {
      throw unavailable();
    }
  }

  public byte[] decrypt(byte[] encrypted) {
    try {
      if (encrypted.length < 29) throw new IllegalArgumentException();
      return cipher(Cipher.DECRYPT_MODE, Arrays.copyOfRange(encrypted, 0, 12))
          .doFinal(Arrays.copyOfRange(encrypted, 12, encrypted.length));
    } catch (Exception failure) {
      throw unavailable();
    }
  }

  private Cipher cipher(int mode, byte[] nonce) throws Exception {
    var cipher = Cipher.getInstance("AES/GCM/NoPadding");
    cipher.init(mode, new SecretKeySpec(key(), "AES"), new GCMParameterSpec(128, nonce));
    cipher.updateAAD(
        "odontocare-integrations-v1".getBytes(java.nio.charset.StandardCharsets.UTF_8));
    return cipher;
  }

  private IllegalStateException unavailable() {
    return new IllegalStateException(
        "No se pudieron proteger o recuperar las credenciales. Revisa la clave de cifrado de la"
            + " instalación.");
  }
}
