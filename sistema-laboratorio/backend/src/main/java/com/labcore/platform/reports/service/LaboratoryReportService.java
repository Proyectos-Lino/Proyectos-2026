package com.labcore.platform.reports;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.labcore.platform.audit.IdentityAccessAuditService;
import com.labcore.platform.common.exception.BusinessRuleViolationException;
import com.labcore.platform.common.exception.ResourceNotFoundException;
import com.labcore.platform.common.export.CsvFile;
import com.labcore.platform.common.i18n.SystemLabel;
import com.labcore.platform.orders.DocumentCodeGenerator;
import com.labcore.platform.orders.ServiceOrder;
import com.labcore.platform.orders.ServiceOrderRepository;
import com.labcore.platform.orders.ServiceOrderService;
import com.labcore.platform.orders.ServiceOrderStatus;
import com.labcore.platform.organization.OrganizationProfileService;
import com.labcore.platform.security.ApplicationUser;
import com.labcore.platform.security.ApplicationUserRepository;
import com.labcore.platform.security.RoleCode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class LaboratoryReportService {
  private static final Set<RoleCode> VIEW_ROLES =
      Set.of(RoleCode.ADMIN, RoleCode.COMMERCIAL, RoleCode.TECHNICAL_MANAGER, RoleCode.VALIDATOR);
  private static final Set<RoleCode> PREPARE_ROLES =
      Set.of(RoleCode.ADMIN, RoleCode.TECHNICAL_MANAGER, RoleCode.VALIDATOR);
  private static final Set<RoleCode> CONTROL_ROLES =
      Set.of(RoleCode.ADMIN, RoleCode.TECHNICAL_MANAGER);
  private static final Set<RoleCode> EXPORT_ROLES = Set.of(RoleCode.ADMIN, RoleCode.COMMERCIAL);

  private final LaboratoryReportRepository reportRepository;
  private final LaboratoryReportHistoryRepository historyRepository;
  private final LaboratoryReportAssembler assembler;
  private final LaboratoryReportPdfGenerator pdfGenerator;
  private final ServiceOrderService serviceOrderService;
  private final ServiceOrderRepository serviceOrderRepository;
  private final ApplicationUserRepository userRepository;
  private final DocumentCodeGenerator codeGenerator;
  private final ObjectMapper objectMapper;
  private final IdentityAccessAuditService identityAccessAuditService;
  private final OrganizationProfileService organizationService;

  public LaboratoryReportService(
      LaboratoryReportRepository reportRepository,
      LaboratoryReportHistoryRepository historyRepository,
      LaboratoryReportAssembler assembler,
      LaboratoryReportPdfGenerator pdfGenerator,
      ServiceOrderService serviceOrderService,
      ServiceOrderRepository serviceOrderRepository,
      ApplicationUserRepository userRepository,
      DocumentCodeGenerator codeGenerator,
      ObjectMapper objectMapper,
      IdentityAccessAuditService identityAccessAuditService,
      OrganizationProfileService organizationService) {
    this.reportRepository = reportRepository;
    this.historyRepository = historyRepository;
    this.assembler = assembler;
    this.pdfGenerator = pdfGenerator;
    this.serviceOrderService = serviceOrderService;
    this.serviceOrderRepository = serviceOrderRepository;
    this.userRepository = userRepository;
    this.codeGenerator = codeGenerator;
    this.objectMapper = objectMapper;
    this.identityAccessAuditService = identityAccessAuditService;
    this.organizationService = organizationService;
  }

  @Transactional(readOnly = true)
  public Page<LaboratoryReportDtos.Summary> list(
      String search,
      LaboratoryReportStatus status,
      UUID serviceOrderId,
      Instant issuedFrom,
      Instant issuedTo,
      UUID laboratoryAreaId,
      Pageable pageable,
      String actorEmail) {
    ApplicationUser actor = currentUser(actorEmail);
    ensureRole(actor, VIEW_ROLES, "consultar informes");
    return reportRepository
        .findAll(
            LaboratoryReportSpecifications.filteredBy(
                search, status, serviceOrderId, issuedFrom, issuedTo, laboratoryAreaId),
            pageable)
        .map(report -> toSummary(report, canSeeIdentityInLists(actor)));
  }

  @Transactional(readOnly = true)
  public java.util.List<LaboratoryReportDtos.PreparationCandidate> preparationCandidates(
      String actorEmail) {
    ensureRole(currentUser(actorEmail), PREPARE_ROLES, "preparar informes");
    return serviceOrderRepository
        .findByStatusOrderByPlannedDeliveryAtAsc(ServiceOrderStatus.FINALIZED)
        .stream()
        .filter(
            order ->
                !reportRepository.existsByServiceOrderIdAndStatus(
                        order.getId(), LaboratoryReportStatus.DRAFT)
                    && !reportRepository.existsByServiceOrderIdAndStatus(
                        order.getId(), LaboratoryReportStatus.ISSUED))
        .map(
            order ->
                new LaboratoryReportDtos.PreparationCandidate(
                    order.getId(), order.getCode(), order.getPlannedDeliveryAt()))
        .toList();
  }

  public LaboratoryReportDtos.Detail createDraft(UUID serviceOrderId, String actorEmail) {
    ensureRole(currentUser(actorEmail), PREPARE_ROLES, "preparar informes");
    ServiceOrder order = serviceOrderService.getRequired(serviceOrderId);
    if (order.getStatus() != ServiceOrderStatus.FINALIZED) {
      throw new BusinessRuleViolationException(
          "La orden de servicio debe estar finalizada antes de preparar su informe.");
    }
    ensureNoOpenReport(serviceOrderId);
    int version = nextVersion(serviceOrderId);
    return createReport(serviceOrderId, version, LaboratoryReportHistoryAction.CREATED, actorEmail);
  }

  public LaboratoryReportDtos.Detail issue(UUID reportId, String actorEmail) {
    ApplicationUser actor = currentUser(actorEmail);
    ensureRole(actor, CONTROL_ROLES, "emitir informes");
    organizationService.ensureAuthorizedSigner(actor);
    LaboratoryReport report = required(reportId);
    if (report.getStatus() != LaboratoryReportStatus.DRAFT) {
      throw new BusinessRuleViolationException("Solo se puede emitir un informe en borrador.");
    }
    LaboratoryReportStatus previous = report.getStatus();
    report.issue(actorEmail);
    LaboratoryReportDtos.Snapshot snapshot = snapshot(report);
    report.replacePdf(
        pdfGenerator.generate(snapshot, report.getStatus(), report.getContentChecksum()));
    recordHistory(
        report,
        LaboratoryReportHistoryAction.ISSUED,
        previous,
        "Informe oficial emitido.",
        actorEmail);
    auditIdentityAccess(
        report,
        "REPORT_ISSUED",
        "Identidad consultada durante la emisión del informe.",
        actorEmail);
    return toDetail(report);
  }

  public LaboratoryReportDtos.Detail cancel(
      UUID reportId, LaboratoryReportDtos.CancelRequest request, String actorEmail) {
    ensureRole(currentUser(actorEmail), CONTROL_ROLES, "anular informes");
    LaboratoryReport report = required(reportId);
    if (report.getStatus() != LaboratoryReportStatus.ISSUED) {
      throw new BusinessRuleViolationException("Solo se puede anular un informe emitido.");
    }
    LaboratoryReportStatus previous = report.getStatus();
    String reason = request.reason().trim();
    report.cancel(actorEmail, reason);
    recordHistory(report, LaboratoryReportHistoryAction.CANCELLED, previous, reason, actorEmail);
    auditIdentityAccess(
        report, "REPORT_CANCELLED", "Identidad consultada al anular el informe.", actorEmail);
    return toDetail(report);
  }

  public LaboratoryReportDtos.Detail reissue(UUID reportId, String actorEmail) {
    ensureRole(currentUser(actorEmail), CONTROL_ROLES, "reemitir informes");
    LaboratoryReport previousReport = required(reportId);
    if (previousReport.getStatus() != LaboratoryReportStatus.CANCELLED) {
      throw new BusinessRuleViolationException(
          "La reemisión requiere que la versión anterior se encuentre anulada.");
    }
    ensureNoOpenReport(previousReport.getServiceOrderId());
    return createReport(
        previousReport.getServiceOrderId(),
        nextVersion(previousReport.getServiceOrderId()),
        LaboratoryReportHistoryAction.REISSUED,
        actorEmail);
  }

  public void cancelOpenReportsForCommercialCorrection(
      UUID serviceOrderId, String reason, String actorEmail) {
    reportRepository
        .findByServiceOrderIdAndStatusIn(
            serviceOrderId,
            java.util.List.of(LaboratoryReportStatus.DRAFT, LaboratoryReportStatus.ISSUED))
        .forEach(
            report -> {
              LaboratoryReportStatus previous = report.getStatus();
              report.cancel(actorEmail, reason);
              report.replacePdf(
                  pdfGenerator.generate(
                      snapshot(report), report.getStatus(), report.getContentChecksum()));
              recordHistory(
                  report,
                  LaboratoryReportHistoryAction.CANCELLED,
                  previous,
                  "Anulado automáticamente por devolución de la orden a corrección comercial: "
                      + reason,
                  actorEmail);
            });
  }

  @Transactional(readOnly = true)
  public LaboratoryReportDtos.Detail get(UUID reportId, String actorEmail) {
    ensureRole(currentUser(actorEmail), VIEW_ROLES, "consultar informes");
    LaboratoryReport report = required(reportId);
    auditIdentityAccess(
        report, "REPORT_VIEWED", "Consulta autorizada del informe y de la identidad.", actorEmail);
    return toDetail(report);
  }

  @Transactional(readOnly = true)
  public LaboratoryReportDtos.PdfFile download(UUID reportId, String actorEmail) {
    ensureRole(currentUser(actorEmail), VIEW_ROLES, "descargar informes");
    LaboratoryReport report = required(reportId);
    auditIdentityAccess(
        report, "REPORT_DOWNLOADED", "Descarga autorizada del informe oficial.", actorEmail);
    return new LaboratoryReportDtos.PdfFile(report.getCode(), report.getPdfContent());
  }

  @Transactional(readOnly = true)
  public Page<LaboratoryReportDtos.HistoryEntry> history(
      UUID reportId, Pageable pageable, String actorEmail) {
    ensureRole(currentUser(actorEmail), VIEW_ROLES, "consultar el historial de informes");
    required(reportId);
    return historyRepository
        .findByLaboratoryReportIdOrderByOccurredAtDesc(reportId, pageable)
        .map(
            history ->
                new LaboratoryReportDtos.HistoryEntry(
                    history.getId(),
                    history.getAction(),
                    history.getPreviousStatus(),
                    history.getNewStatus(),
                    history.getChangeNote(),
                    history.getPerformedBy(),
                    history.getOccurredAt()));
  }

  @Transactional(readOnly = true)
  public byte[] export(
      String search,
      LaboratoryReportStatus status,
      UUID serviceOrderId,
      Instant issuedFrom,
      Instant issuedTo,
      UUID laboratoryAreaId,
      String actorEmail) {
    ensureRole(currentUser(actorEmail), EXPORT_ROLES, "exportar informes con identidad");
    CsvFile csv =
        new CsvFile()
            .row(
                "Código", "Orden de servicio", "Cliente", "Versión", "Estado", "Creado", "Emitido");
    reportRepository
        .findAll(
            LaboratoryReportSpecifications.filteredBy(
                search, status, serviceOrderId, issuedFrom, issuedTo, laboratoryAreaId),
            PageRequest.of(0, 10_000, Sort.by(Sort.Direction.DESC, "createdAt")))
        .map(report -> toSummary(report, true))
        .forEach(
            report ->
                csv.row(
                    report.code(),
                    report.serviceOrderCode(),
                    report.clientName(),
                    report.version(),
                    SystemLabel.forValue(report.status()),
                    report.createdAt(),
                    report.issuedAt()));
    return csv.bytes();
  }

  private LaboratoryReportDtos.Detail createReport(
      UUID serviceOrderId, int version, LaboratoryReportHistoryAction action, String actorEmail) {
    String code = codeGenerator.next("IR");
    LaboratoryReportDtos.Snapshot snapshot = assembler.assemble(serviceOrderId, code, version);
    String snapshotJson = serialize(snapshot);
    String checksum = checksum(snapshotJson);
    byte[] pdf = pdfGenerator.generate(snapshot, LaboratoryReportStatus.DRAFT, checksum);
    LaboratoryReport report =
        reportRepository.save(
            new LaboratoryReport(
                code, serviceOrderId, version, snapshotJson, pdf, checksum, actorEmail));
    recordHistory(
        report,
        action,
        null,
        action == LaboratoryReportHistoryAction.REISSUED
            ? "Nueva versión preparada a partir de un informe anulado."
            : "Borrador de informe preparado con resultados validados.",
        actorEmail);
    auditIdentityAccess(
        report,
        action == LaboratoryReportHistoryAction.REISSUED ? "REPORT_REISSUED" : "REPORT_PREPARED",
        action == LaboratoryReportHistoryAction.REISSUED
            ? "Identidad consultada para preparar una nueva versión del informe."
            : "Identidad consultada para preparar el informe de resultados.",
        actorEmail);
    return toDetail(report);
  }

  private void ensureNoOpenReport(UUID serviceOrderId) {
    if (reportRepository.existsByServiceOrderIdAndStatus(
            serviceOrderId, LaboratoryReportStatus.DRAFT)
        || reportRepository.existsByServiceOrderIdAndStatus(
            serviceOrderId, LaboratoryReportStatus.ISSUED)) {
      throw new BusinessRuleViolationException(
          "La orden ya cuenta con un informe vigente o pendiente de emisión.");
    }
  }

  private int nextVersion(UUID serviceOrderId) {
    return reportRepository
        .findFirstByServiceOrderIdOrderByReportVersionDesc(serviceOrderId)
        .map(report -> report.getReportVersion() + 1)
        .orElse(1);
  }

  private LaboratoryReportDtos.Summary toSummary(LaboratoryReport report, boolean includeIdentity) {
    LaboratoryReportDtos.Snapshot reportSnapshot = snapshot(report);
    return new LaboratoryReportDtos.Summary(
        report.getId(),
        report.getCode(),
        report.getServiceOrderId(),
        reportSnapshot.serviceOrderCode(),
        includeIdentity ? reportSnapshot.clientName() : null,
        report.getReportVersion(),
        report.getStatus(),
        report.getCreatedAt(),
        report.getIssuedAt());
  }

  private LaboratoryReportDtos.Detail toDetail(LaboratoryReport report) {
    return new LaboratoryReportDtos.Detail(
        report.getId(),
        report.getCode(),
        report.getServiceOrderId(),
        report.getReportVersion(),
        report.getStatus(),
        report.getContentChecksum(),
        report.getCreatedBy(),
        report.getCreatedAt(),
        report.getIssuedBy(),
        report.getIssuedAt(),
        report.getCancelledBy(),
        report.getCancelledAt(),
        report.getCancellationReason(),
        snapshot(report));
  }

  private LaboratoryReportDtos.Snapshot snapshot(LaboratoryReport report) {
    try {
      return objectMapper.readValue(report.getSnapshotJson(), LaboratoryReportDtos.Snapshot.class);
    } catch (JsonProcessingException exception) {
      throw new IllegalStateException(
          "El snapshot del informe no puede ser interpretado.", exception);
    }
  }

  private String serialize(LaboratoryReportDtos.Snapshot snapshot) {
    try {
      return objectMapper.writeValueAsString(snapshot);
    } catch (JsonProcessingException exception) {
      throw new IllegalStateException(
          "No fue posible conservar el snapshot del informe.", exception);
    }
  }

  private String checksum(String content) {
    try {
      byte[] digest =
          MessageDigest.getInstance("SHA-256").digest(content.getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(digest);
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException("SHA-256 no está disponible.", exception);
    }
  }

  private LaboratoryReport required(UUID id) {
    return reportRepository
        .findById(id)
        .orElseThrow(() -> new ResourceNotFoundException("el informe de resultados", id));
  }

  private ApplicationUser currentUser(String email) {
    ApplicationUser user =
        userRepository
            .findByEmailIgnoreCase(email)
            .orElseThrow(
                () -> new ResourceNotFoundException("el usuario autenticado", UUID.randomUUID()));
    if (!user.isActive()) {
      throw new BusinessRuleViolationException("El usuario autenticado se encuentra desactivado.");
    }
    return user;
  }

  private void ensureRole(ApplicationUser user, Set<RoleCode> roles, String operation) {
    boolean allowed = user.getRoles().stream().anyMatch(role -> roles.contains(role.getCode()));
    if (!allowed) {
      throw new BusinessRuleViolationException(
          "Tu usuario no cuenta con permisos para " + operation + ".");
    }
  }

  private void recordHistory(
      LaboratoryReport report,
      LaboratoryReportHistoryAction action,
      LaboratoryReportStatus previousStatus,
      String note,
      String actorEmail) {
    historyRepository.save(
        new LaboratoryReportHistory(
            report.getId(), action, previousStatus, report.getStatus(), note, actorEmail));
  }

  private boolean canSeeIdentityInLists(ApplicationUser actor) {
    return actor.getRoles().stream().anyMatch(role -> EXPORT_ROLES.contains(role.getCode()));
  }

  private void auditIdentityAccess(
      LaboratoryReport report, String action, String reason, String actorEmail) {
    identityAccessAuditService.recordReportAccess(
        report.getId(), report.getCode(), action, reason, actorEmail);
  }
}
