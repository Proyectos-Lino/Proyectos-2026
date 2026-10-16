package com.odontocare.finance.service;

import com.odontocare.audit.service.AuditService;
import com.odontocare.clinical.service.ClinicalAccess;
import com.odontocare.documents.repository.DocumentPolicyRepository;
import com.odontocare.documents.service.DocumentValidator;
import com.odontocare.finance.dto.PaymentContracts.*;
import com.odontocare.finance.model.*;
import com.odontocare.finance.repository.*;
import com.odontocare.installation.repository.*;
import com.odontocare.patients.repository.PatientRepository;
import com.odontocare.shared.pagination.*;
import com.odontocare.shared.web.ApiException;
import java.security.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class FinancialDocumentService {
  private final FinancialDocumentRepository documents;
  private final FinancialContentRepository contents;
  private final MoneyMovementRepository movements;
  private final InstallationProfileRepository profiles;
  private final InstallationLogoRepository logos;
  private final PatientRepository patients;
  private final DocumentValidator validator;
  private final DocumentPolicyRepository policies;
  private final FinancialPdfService pdf;
  private final FinancialPdfContentService pdfContent;
  private final ClinicalAccess access;
  private final AuditService audit;
  private final FinanceOperationRepository operations;

  public FinancialDocumentService(
      FinancialDocumentRepository documents,
      FinancialContentRepository contents,
      MoneyMovementRepository movements,
      InstallationProfileRepository profiles,
      InstallationLogoRepository logos,
      PatientRepository patients,
      DocumentValidator validator,
      DocumentPolicyRepository policies,
      FinancialPdfService pdf,
      ClinicalAccess access,
      AuditService audit,
      FinanceOperationRepository operations,
      FinancialPdfContentService pdfContent) {
    this.documents = documents;
    this.contents = contents;
    this.movements = movements;
    this.profiles = profiles;
    this.logos = logos;
    this.patients = patients;
    this.validator = validator;
    this.policies = policies;
    this.pdf = pdf;
    this.pdfContent = pdfContent;
    this.access = access;
    this.audit = audit;
    this.operations = operations;
  }

  public String nextCode() {
    var p = profiles.lockInstallation().orElseThrow();
    String code =
        p.getReceiptPrefix()
            + "-"
            + String.format(java.util.Locale.ROOT, "%06d", p.getReceiptNextNumber());
    if (documents.existsByFileNameAndGeneratedTrue(code + ".pdf"))
      throw ApiException.conflict(
          "El correlativo ya fue emitido. Configura otro prefijo o número siguiente de"
              + " constancia.");
    if (p.getReceiptNextNumber() == Integer.MAX_VALUE)
      throw ApiException.conflict("Configura un nuevo correlativo de constancias.");
    p.setReceiptNextNumber(p.getReceiptNextNumber() + 1);
    profiles.saveAndFlush(p);
    return code;
  }

  public void receipt(MoneyMovement m, List<Allocation> rows, String reason) {
    var content = pdfContent.receipt(m, rows, reason);
    store(
        m.getId(),
        m.getPatientId(),
        m.getReceiptCode() + ".pdf",
        "application/pdf",
        render("Constancia de " + kind(m.getKind()), m.getReceiptCode(), content),
        "Constancia interna " + m.getReceiptCode(),
        true);
  }

  private String kind(String value) {
    return Map.of(
            "PAYMENT",
            "Abono",
            "REFUND",
            "Devolución",
            "REVERSAL",
            "Reversión de pago",
            "EXPENSE",
            "Egreso",
            "EXPENSE_REVERSAL",
            "Reversión de egreso")
        .get(value);
  }

  private byte[] render(String title, String code, com.odontocare.shared.pdf.PdfContent content) {
    var identity = profiles.lockInstallation().orElseThrow();
    var logo = logos.findById((short) 1).map(l -> l.getContent()).orElse(null);
    return pdf.render(identity, logo, title, code, content);
  }

  private FinancialDocument store(
      UUID movement,
      UUID patient,
      String name,
      String type,
      byte[] bytes,
      String description,
      boolean generated) {
    try {
      var d = new FinancialDocument();
      d.setMovementId(movement);
      d.setPatientId(patient);
      d.setFileName(name);
      d.setMediaType(type);
      d.setByteSize((long) bytes.length);
      d.setSha256(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)));
      d.setDescription(description.strip());
      d.setActorName(access.actor().getDisplayName());
      d.setGenerated(generated);
      documents.saveAndFlush(d);
      var content = new FinancialContent();
      content.setId(d.getId());
      content.setContent(bytes);
      contents.saveAndFlush(content);
      return d;
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }

  @Transactional
  public DocumentResponse upload(SupportRequest r, MultipartFile file) {
    var m = movements.findById(r.movementId()).orElseThrow(ApiException::notFound);
    String needed = m.getPatientId() == null ? "EXPENSES_WRITE" : "PAYMENTS_WRITE";
    if (!access.actor().getPermissions().contains(needed)) throw ApiException.forbidden();
    var validated =
        validator.validate(file, policies.findById((short) 1).orElseThrow().getMaxFileMiB());
    var d =
        store(
            m.getId(),
            m.getPatientId(),
            validated.name(),
            validated.mediaType(),
            validated.bytes(),
            r.description(),
            false);
    audit.record(
        "FINANCIAL_DOCUMENT_UPLOADED",
        "FINANCIAL_DOCUMENT",
        d.getId(),
        "Adjuntó sustento financiero");
    return response(d);
  }

  @Transactional
  public DocumentResponse statement(StatementRequest r) {
    patients.lockById(r.patientId()).orElseThrow(ApiException::notFound);
    String hash = fingerprint(r);
    var prior = operations.findByRequestKey(r.requestKey());
    if (prior.isPresent()) {
      if (!prior.get().getAction().equals("STATEMENT")
          || !prior.get().getFingerprint().equals(hash))
        throw ApiException.conflict("La clave ya corresponde a otra operación.");
      return response(documents.findById(prior.get().getResultId()).orElseThrow());
    }
    var content = pdfContent.statement(r.patientId(), r.currency());
    String code = nextCode();
    var d =
        store(
            null,
            r.patientId(),
            code + ".pdf",
            "application/pdf",
            render("Estado de cuenta", code, content),
            "Estado de cuenta " + code,
            true);
    var op = new FinanceOperation();
    op.setRequestKey(r.requestKey());
    op.setAction("STATEMENT");
    op.setFingerprint(hash);
    op.setResultId(d.getId());
    op.setReason("Emitió estado de cuenta");
    op.setActorName(access.actor().getDisplayName());
    operations.saveAndFlush(op);
    audit.record(
        "STATEMENT_ISSUED", "FINANCIAL_DOCUMENT", d.getId(), "Emitió estado de cuenta histórico");
    return response(d);
  }

  public void cashReport(CashSession session) {
    String code = nextCode();
    var bytes = render("Arqueo de caja", code, pdfContent.cash(session));
    storeCash(session.getId(), code, bytes);
  }

  private void storeCash(UUID cash, String code, byte[] bytes) {
    try {
      var d = new FinancialDocument();
      d.setCashSessionId(cash);
      d.setFileName(code + ".pdf");
      d.setMediaType("application/pdf");
      d.setByteSize((long) bytes.length);
      d.setSha256(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)));
      d.setDescription("Arqueo de caja " + code);
      d.setActorName(access.actor().getDisplayName());
      d.setGenerated(true);
      documents.saveAndFlush(d);
      var content = new FinancialContent();
      content.setId(d.getId());
      content.setContent(bytes);
      contents.saveAndFlush(content);
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }

  @Transactional
  public Download cashReport(UUID id) {
    var d = documents.findByCashSessionIdAndGeneratedTrue(id).orElseThrow(ApiException::notFound);
    audit.record("CASH_REPORT_READ", "CASH", id, "Descargó arqueo histórico");
    return new Download(
        d.getFileName(), d.getMediaType(), contents.findById(d.getId()).orElseThrow().getContent());
  }

  private String fingerprint(Object r) {
    try {
      return HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256")
                  .digest(new tools.jackson.databind.ObjectMapper().writeValueAsBytes(r)));
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }

  @Transactional
  public PageResponse<DocumentResponse> list(
      UUID patient, UUID movement, Boolean generated, PageQuery query) {
    var spec =
        SearchSpecifications.<FinancialDocument>text(
                query.getSearch(), "fileName", "description", "actorName")
            .and(SearchSpecifications.equal("patientId", patient))
            .and(SearchSpecifications.equal("movementId", movement))
            .and(SearchSpecifications.equal("generated", generated));
    audit.record(
        "FINANCIAL_DOCUMENT_LIST", "FINANCIAL_DOCUMENT", patient, "Consultó metadatos financieros");
    return PageResponse.of(
        documents
            .findAll(
                spec,
                query.pageable(
                    Map.of("name", "fileName", "createdAt", "createdAt", "byteSize", "byteSize")))
            .map(this::response));
  }

  @Transactional
  public Download download(UUID id) {
    var d = documents.findById(id).orElseThrow(ApiException::notFound);
    audit.record(
        "FINANCIAL_DOCUMENT_READ", "FINANCIAL_DOCUMENT", id, "Recuperó documento financiero");
    return new Download(
        d.getFileName(), d.getMediaType(), contents.findById(id).orElseThrow().getContent());
  }

  @Transactional
  public DocumentResponse receipt(UUID id) {
    return response(
        documents.findByMovementIdAndGeneratedTrue(id).orElseThrow(ApiException::notFound));
  }

  private DocumentResponse response(FinancialDocument d) {
    return new DocumentResponse(
        d.getId(),
        d.getMovementId(),
        d.getPatientId(),
        d.getFileName(),
        d.getMediaType(),
        d.getByteSize(),
        d.getSha256(),
        d.getDescription(),
        d.getActorName(),
        d.getGenerated(),
        d.getCreatedAt());
  }
}
