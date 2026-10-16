package com.odontocare.installation.service;

import com.odontocare.audit.service.AuditService;
import com.odontocare.installation.model.InstallationLogo;
import com.odontocare.installation.repository.*;
import com.odontocare.shared.web.ApiException;
import java.io.*;
import java.util.Locale;
import javax.imageio.ImageIO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class LogoService {
  private static final int MAX_BYTES = 2 * 1024 * 1024;
  private static final int MAX_DIMENSION = 4096;
  private final InstallationLogoRepository logos;
  private final InstallationProfileRepository profiles;
  private final ConfigurationLock lock;
  private final AuditService audit;

  public LogoService(
      InstallationLogoRepository logos,
      InstallationProfileRepository profiles,
      ConfigurationLock lock,
      AuditService audit) {
    this.logos = logos;
    this.profiles = profiles;
    this.lock = lock;
    this.audit = audit;
  }

  @Transactional(readOnly = true)
  public LogoPayload getLogo() {
    var logo = logos.findById((short) 1).orElseThrow(ApiException::notFound);
    return new LogoPayload(logo.getContentType(), logo.getContent());
  }

  @Transactional
  public void upload(MultipartFile file, long version) {
    if (file.isEmpty() || file.getSize() > MAX_BYTES)
      throw ApiException.badRequest("El logo debe ser PNG o JPG y pesar como máximo 2 MiB.");
    byte[] bytes;
    try {
      bytes = file.getBytes();
    } catch (IOException exception) {
      throw ApiException.badRequest("No pudimos leer el archivo.");
    }
    String contentType = validateImage(bytes);
    var profile = lock.acquire();
    if (profile.getVersion() != version)
      throw ApiException.conflict("La configuración cambió. Actualiza antes de cambiar el logo.");
    var logo = logos.findById((short) 1).orElseGet(InstallationLogo::new);
    logo.setContent(bytes);
    logo.setContentType(contentType);
    logos.save(logo);
    profile.advanceLogoRevision();
    profiles.save(profile);
    audit.record(
        "LOGO_UPDATED",
        "SETTINGS",
        1,
        "Actualizó el logo del consultorio almacenado en PostgreSQL.");
  }

  @Transactional
  public void remove(long version) {
    var profile = lock.acquire();
    if (profile.getVersion() != version)
      throw ApiException.conflict("La configuración cambió. Actualiza antes de retirar el logo.");
    logos.findById((short) 1).ifPresent(logos::delete);
    profile.advanceLogoRevision();
    profiles.save(profile);
    audit.record("LOGO_REMOVED", "SETTINGS", 1, "Retiró el logo del consultorio.");
  }

  private String validateImage(byte[] bytes) {
    try (var input = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
      var readers = ImageIO.getImageReaders(input);
      if (!readers.hasNext())
        throw ApiException.badRequest("El archivo no contiene una imagen PNG o JPG válida.");
      var reader = readers.next();
      try {
        reader.setInput(input, true, true);
        String format = reader.getFormatName().toLowerCase(Locale.ROOT);
        if (!format.equals("png") && !format.equals("jpeg") && !format.equals("jpg"))
          throw ApiException.badRequest("Utiliza un logo PNG o JPG.");
        if (reader.getWidth(0) > MAX_DIMENSION || reader.getHeight(0) > MAX_DIMENSION)
          throw ApiException.badRequest("El logo puede tener como máximo 4096 píxeles por lado.");
        if (reader.read(0) == null)
          throw ApiException.badRequest("La imagen está incompleta o dañada.");
        return format.equals("png") ? "image/png" : "image/jpeg";
      } finally {
        reader.dispose();
      }
    } catch (IOException exception) {
      throw ApiException.badRequest("La imagen está incompleta o dañada.");
    }
  }

  public record LogoPayload(String contentType, byte[] content) {}
}
