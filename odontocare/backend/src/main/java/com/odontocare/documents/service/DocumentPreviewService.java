package com.odontocare.documents.service;

import com.odontocare.shared.web.ApiException;
import java.io.*;
import javax.imageio.ImageIO;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.rendering.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DocumentPreviewService {
  private final PatientDocumentService documents;

  public DocumentPreviewService(PatientDocumentService documents) {
    this.documents = documents;
  }

  public record PagePreview(byte[] bytes, int page, int pages) {}

  @Transactional
  public PagePreview preview(java.util.UUID id, int page) {
    var document = documents.download(id, false);
    if (!document.mediaType().equals("application/pdf"))
      throw ApiException.badRequest("La vista por páginas corresponde a un PDF.");
    return render(document.bytes(), page);
  }

  public static PagePreview render(byte[] content, int page) {
    try (var pdf = Loader.loadPDF(content)) {
      int count = pdf.getNumberOfPages();
      if (page < 0 || page >= count)
        throw ApiException.badRequest("La página solicitada no existe.");
      var box = pdf.getPage(page).getCropBox();
      if (box.getWidth() <= 0
          || box.getHeight() <= 0
          || !Float.isFinite(box.getWidth())
          || !Float.isFinite(box.getHeight()))
        throw ApiException.badRequest("La página PDF tiene dimensiones no válidas.");
      float scale = Math.min(1.5f, 1600f / Math.max(box.getWidth(), box.getHeight()));
      var renderer = new PDFRenderer(pdf);
      renderer.setSubsamplingAllowed(true);
      var image = renderer.renderImage(page, scale, ImageType.RGB);
      try (var output = new ByteArrayOutputStream()) {
        ImageIO.write(image, "png", output);
        return new PagePreview(output.toByteArray(), page, count);
      }
    } catch (IOException exception) {
      throw ApiException.badRequest(
          "No se pudo visualizar esta página. Puedes descargar el original.");
    }
  }
}
