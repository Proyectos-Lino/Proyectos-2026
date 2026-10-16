package com.odontocare.documents.service;

import com.odontocare.shared.web.ApiException;
import java.io.*;
import java.util.*;
import javax.imageio.ImageIO;
import javax.imageio.stream.MemoryCacheImageInputStream;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.cos.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

@Component
public class DocumentValidator {
  public record ValidatedFile(String name, String mediaType, byte[] bytes) {}

  public ValidatedFile validate(MultipartFile file, int maxMiB) {
    if (file.isEmpty()) throw ApiException.badRequest("El archivo está vacío.");
    if (file.getSize() > maxMiB * 1024L * 1024L)
      throw new ApiException(
          HttpStatus.PAYLOAD_TOO_LARGE, "El archivo supera el límite de " + maxMiB + " MiB.");
    String name = Optional.ofNullable(file.getOriginalFilename()).orElse("archivo");
    name = name.replaceAll("[\\/\r\n\u0000-\u001f]", "_");
    if (name.length() > 180) name = name.substring(name.length() - 180);
    String extension = name.substring(name.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
    String mime =
        Map.of(
                "jpg",
                "image/jpeg",
                "jpeg",
                "image/jpeg",
                "png",
                "image/png",
                "webp",
                "image/webp",
                "pdf",
                "application/pdf")
            .get(extension);
    if (mime == null) throw ApiException.badRequest("Solo se admiten JPG/JPEG, PNG, WebP y PDF.");
    try {
      byte[] bytes = file.getBytes();
      if (mime.equals("application/pdf")) validatePdf(bytes);
      else validateImage(bytes, mime);
      return new ValidatedFile(name, mime, bytes);
    } catch (ApiException exception) {
      throw exception;
    } catch (IOException | RuntimeException exception) {
      throw ApiException.badRequest(
          "El contenido del archivo está dañado o no coincide con el formato indicado.");
    }
  }

  private void validateImage(byte[] bytes, String mime) throws IOException {
    try (var stream = new MemoryCacheImageInputStream(new ByteArrayInputStream(bytes))) {
      var readers = ImageIO.getImageReaders(stream);
      if (!readers.hasNext())
        throw ApiException.badRequest("La imagen no tiene un formato admitido.");
      var reader = readers.next();
      try {
        String format = reader.getFormatName().toLowerCase(Locale.ROOT);
        String detected =
            Map.of(
                    "jpeg",
                    "image/jpeg",
                    "jpg",
                    "image/jpeg",
                    "png",
                    "image/png",
                    "webp",
                    "image/webp")
                .get(format);
        if (!mime.equals(detected))
          throw ApiException.badRequest("El contenido de la imagen no coincide con su extensión.");
        reader.setInput(stream, true, true);
        int width = reader.getWidth(0), height = reader.getHeight(0);
        if (width < 1 || height < 1 || (long) width * height > 40000000)
          throw ApiException.badRequest("La imagen supera el límite de 40 millones de píxeles.");
        if (reader.read(0) == null) throw ApiException.badRequest("La imagen no se puede leer.");
      } finally {
        reader.dispose();
      }
    }
  }

  private void validatePdf(byte[] bytes) throws IOException {
    if (bytes.length < 8
        || !new String(bytes, 0, 5, java.nio.charset.StandardCharsets.US_ASCII).equals("%PDF-"))
      throw ApiException.badRequest("El contenido no es un PDF.");
    try (var pdf = Loader.loadPDF(bytes)) {
      if (pdf.isEncrypted() || pdf.getNumberOfPages() < 1 || pdf.getNumberOfPages() > 1000)
        throw ApiException.badRequest(
            "El PDF debe poder abrirse sin contraseña y contener de 1 a 1000 páginas.");
      Set<COSBase> visited = Collections.newSetFromMap(new IdentityHashMap<>());
      int[] budget = {100000};
      inspect(pdf.getDocumentCatalog().getCOSObject(), visited, budget, 0);
    }
  }

  private void inspect(COSBase value, Set<COSBase> visited, int[] budget, int depth) {
    if (value == null || !visited.add(value)) return;
    if (--budget[0] < 0 || depth > 100)
      throw ApiException.badRequest("La estructura del PDF es demasiado compleja.");
    if (value instanceof COSObject object) {
      inspect(object.getObject(), visited, budget, depth + 1);
    } else if (value instanceof COSDictionary dictionary) {
      for (var key : dictionary.keySet()) {
        if (Set.of("JS", "JavaScript", "Launch", "EmbeddedFiles", "XFA", "RichMedia")
            .contains(key.getName()))
          throw ApiException.badRequest(
              "El PDF contiene contenido activo o archivos incrustados no admitidos.");
        var item = dictionary.getDictionaryObject(key);
        if (key.getName().equals("S")
            && item instanceof COSName name
            && Set.of("JavaScript", "Launch", "SubmitForm", "ImportData", "GoToR")
                .contains(name.getName()))
          throw ApiException.badRequest("El PDF contiene acciones no admitidas.");
        inspect(item, visited, budget, depth + 1);
      }
    } else if (value instanceof COSArray array) {
      for (var item : array) inspect(item, visited, budget, depth + 1);
    }
  }
}
