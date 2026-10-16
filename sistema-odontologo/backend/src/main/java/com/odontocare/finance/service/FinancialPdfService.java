package com.odontocare.finance.service;

import com.odontocare.installation.model.InstallationProfile;
import com.odontocare.shared.pdf.*;
import java.util.*;
import org.springframework.stereotype.Service;

@Service
public class FinancialPdfService {
  private final InstitutionalPdfRenderer renderer;

  public FinancialPdfService(InstitutionalPdfRenderer renderer) {
    this.renderer = renderer;
  }

  public byte[] render(
      InstallationProfile identity, byte[] logo, String title, String code, PdfContent content) {
    return renderer.render(identity, logo, title, code, content, false);
  }
}
