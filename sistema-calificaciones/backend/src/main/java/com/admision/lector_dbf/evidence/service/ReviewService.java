package com.admision.lector_dbf.evidence.service;

import com.admision.lector_dbf.evidence.model.ReviewModels;
import com.admision.lector_dbf.evidence.repository.ReviewRepository;
import com.admision.lector_dbf.audit.repository.AuditRepository;
import com.admision.lector_dbf.evaluation.repository.EvaluationRepository;
import com.admision.lector_dbf.publication.repository.PublicationRepository;

import com.admision.lector_dbf.exam.repository.ExamRepository;
import com.admision.lector_dbf.security.OrganizationAccess;
import com.admision.lector_dbf.shared.ApiException;
import com.admision.lector_dbf.shared.PageRequest;
import com.admision.lector_dbf.shared.PageResult;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

@Service
public class ReviewService {
    private final ReviewRepository reviews;
    private final ExamRepository exams;
    private final long maxBytes;
    private final OrganizationAccess access;
    private final EvaluationRepository evaluations;
    private final AuditRepository audit;
    private final PublicationRepository publications;

    public ReviewService(ReviewRepository reviews, ExamRepository exams,
                         @Value("${app.evidence.max-bytes}") long maxBytes, OrganizationAccess access,
                         EvaluationRepository evaluations, AuditRepository audit, PublicationRepository publications) {
        this.reviews = reviews;
        this.exams = exams;
        this.maxBytes = maxBytes;
        this.access = access;
        this.evaluations = evaluations;
        this.audit = audit;
        this.publications = publications;
    }

    public PageResult<ReviewModels.ReviewCase> cases(long organizationId, long evaluationId,
            String query, String status, int page, int size, Authentication auth) {
        return cases(organizationId,evaluationId,query,status,page,size,"ALL",auth);
    }
    public PageResult<ReviewModels.ReviewCase> cases(long organizationId,long evaluationId,String query,String status,int page,int size,String assignment,Authentication auth) {
        long actor=access.require(organizationId, auth).id();
        assignment(assignment);
        evaluations.require(organizationId, evaluationId);
        new PageRequest(page, size);
        if (!List.of("", "PENDIENTES", "ABIERTA", "EN_REVISION", "APROBADA", "RECHAZADA", "ANULADA").contains(status))
            throw ApiException.badRequest("Estado de revisión inválido");
        return reviews.cases(organizationId, evaluationId, query, status, page, size,assignment,actor);
    }

    public ReviewModels.Neighbors neighbors(long organization,long evaluation,long sheet,String assignment,Authentication auth) {
        long actor=access.require(organization,auth).id(); assignment(assignment);
        reviews.sheet(organization,evaluation,sheet).orElseThrow(()->ApiException.notFound("Examen"));
        return reviews.neighbors(evaluation,sheet,assignment,actor);
    }
    private void assignment(String value) {
        if (!List.of("ALL","MINE","UNASSIGNED").contains(value)) throw ApiException.badRequest("Filtro de responsable inválido");
    }

    public PageResult<ReviewModels.Assignee> assignees(long organizationId, long evaluationId,
            String query, int page, int size, Authentication auth) {
        access.require(organizationId, auth);
        evaluations.require(organizationId, evaluationId);
        new PageRequest(page, size);
        return reviews.assignees(organizationId, query, page, size);
    }

    @Transactional
    public void assign(long organizationId, long evaluationId, long reviewId, Long assigneeId, Authentication auth) {
        long userId = access.require(organizationId, auth, "ADMINISTRADOR", "REVISOR").id();
        ensureEditable(organizationId, evaluationId);
        reviews.review(organizationId, evaluationId, reviewId).orElseThrow(() -> ApiException.notFound("Revisión"));
        if (assigneeId != null && !reviews.eligibleAssignee(organizationId, assigneeId))
            throw ApiException.badRequest("El responsable debe ser un administrador o revisor activo de la organización");
        if (reviews.assign(reviewId, assigneeId) == 0) throw ApiException.conflict("La revisión ya fue resuelta");
        audit.record(organizationId, evaluationId, userId, "REVISION_ASIGNADA", "REVISION", reviewId,
                "{\"responsableId\":" + (assigneeId == null ? "null" : assigneeId) + "}");
    }

    private void ensureEditable(long organizationId, long evaluationId) {
        publications.assertMutable(organizationId, evaluationId);
    }

    public List<ReviewModels.Review> list(long organizationId, long evaluationId, long sheetId, Authentication auth) {
        access.require(organizationId, auth);
        reviews.sheet(organizationId, evaluationId, sheetId).orElseThrow(() -> ApiException.notFound("Examen"));
        return reviews.reviews(organizationId, evaluationId, sheetId);
    }

    @Transactional
    public long openForUser(long organizationId, long evaluationId, long sheetId, String reason, Authentication auth) {
        long userId = access.require(organizationId, auth, "ADMINISTRADOR", "REVISOR").id();
        return open(organizationId, evaluationId, sheetId, reason, userId);
    }

    @Transactional
    public void resolveForUser(long organizationId, long evaluationId, long reviewId,
                               String decision, String explanation, Authentication auth) {
        long userId = access.require(organizationId, auth, "ADMINISTRADOR", "REVISOR").id();
        resolve(organizationId, evaluationId, reviewId, decision, explanation, userId);
    }

    public List<ReviewModels.Evidence> evidence(long organizationId, long evaluationId, long reviewId, Authentication auth) {
        access.require(organizationId, auth);
        reviews.review(organizationId, evaluationId, reviewId).orElseThrow(() -> ApiException.notFound("Revisión"));
        return reviews.evidence(organizationId, evaluationId, reviewId);
    }

    @Transactional
    public long attachForUser(long organizationId, long evaluationId, long reviewId,
                              MultipartFile file, Authentication auth) {
        long userId = access.require(organizationId, auth, "ADMINISTRADOR", "REVISOR").id();
        return attach(organizationId, evaluationId, reviewId, file, userId);
    }

    public ReviewModels.EvidenceFile file(long organizationId, long evaluationId, long evidenceId, Authentication auth) {
        access.require(organizationId, auth);
        return reviews.file(organizationId, evaluationId, evidenceId)
                .orElseThrow(() -> ApiException.notFound("Evidencia"));
    }

    @Transactional
    public long annul(long organizationId, long evaluationId, long sheetId, String reason, Authentication auth) {
        long userId = access.require(organizationId, auth, "ADMINISTRADOR", "REVISOR").id();
        ensureEditable(organizationId, evaluationId);
        var exam = exams.find(organizationId, evaluationId, sheetId)
                .orElseThrow(() -> ApiException.notFound("Examen"));
        if ("ANULADA".equals(exam.status())) throw ApiException.conflict("El examen ya está anulado");
        if (reviews.hasOpen(sheetId)) throw ApiException.conflict("Este examen tiene una revisión pendiente");
        long reviewId = reviews.open(sheetId, reason.trim(), userId);
        reviews.resolve(reviewId, "ANULADA", reason.trim(), userId);
        reviews.setSheetStatus(sheetId, "ANULADA", reason.trim());
        exams.recalculateRanking(evaluationId);
        audit.record(organizationId, evaluationId, userId, "EXAMEN_ANULADO", "EXAMEN", sheetId, "{}");
        return reviewId;
    }

    @Transactional
    public long restore(long organizationId, long evaluationId, long sheetId, Authentication auth) {
        long userId = access.require(organizationId, auth, "ADMINISTRADOR", "REVISOR").id();
        ensureEditable(organizationId, evaluationId);
        var exam = exams.find(organizationId, evaluationId, sheetId)
                .orElseThrow(() -> ApiException.notFound("Examen"));
        if (!"ANULADA".equals(exam.status())) throw ApiException.conflict("El examen no está anulado");
        long reviewId = reviews.open(sheetId, "Reversión de anulación", userId);
        reviews.resolve(reviewId, "APROBADA", "Anulación revertida", userId);
        reviews.setSheetStatus(sheetId, "VALIDADA", null);
        exams.recalculateRanking(evaluationId);
        audit.record(organizationId, evaluationId, userId, "ANULACION_REVERTIDA", "EXAMEN", sheetId, "{}");
        return reviewId;
    }

    @Transactional
    public void updateReason(long organizationId, long evaluationId, long sheetId,
                             String reason, Authentication auth) {
        long userId = access.require(organizationId, auth, "ADMINISTRADOR", "REVISOR").id();
        ensureEditable(organizationId, evaluationId);
        String previous = reviews.sheetObservation(organizationId, evaluationId, sheetId).orElse("");
        if (reviews.updateSheetReason(organizationId, evaluationId, sheetId, reason.trim()) == 0)
            throw ApiException.conflict("Solo se puede actualizar el motivo de un examen anulado");
        audit.record(organizationId, evaluationId, userId, "MOTIVO_ACTUALIZADO", "EXAMEN", sheetId,
                Map.of("anterior", previous, "nuevo", reason.trim()));
    }

    @Transactional
    public void deleteEvidence(long organizationId, long evaluationId, long evidenceId, Authentication auth) {
        long userId = access.require(organizationId, auth, "ADMINISTRADOR", "REVISOR").id();
        ensureEditable(organizationId, evaluationId);
        if (reviews.deleteEvidence(organizationId, evaluationId, evidenceId) == 0)
            throw ApiException.notFound("Evidencia");
        audit.record(organizationId, evaluationId, userId, "EVIDENCIA_ELIMINADA", "EVIDENCIA", evidenceId, "{}");
    }

    @Transactional
    public long open(long organizationId, long evaluationId, long sheetId, String reason, long userId) {
        ensureEditable(organizationId, evaluationId);
        reviews.sheet(organizationId, evaluationId, sheetId).orElseThrow(() -> ApiException.notFound("Examen"));
        if (reviews.hasOpen(sheetId)) throw ApiException.conflict("Este examen tiene una revisión pendiente");
        long id = reviews.open(sheetId, reason.trim(), userId);
        reviews.markObserved(sheetId, reason.trim());
        exams.recalculateRanking(evaluationId);
        audit.record(organizationId, evaluationId, userId, "REVISION_ABIERTA", "REVISION", id, "{}");
        return id;
    }

    @Transactional
    public void resolve(long organizationId, long evaluationId, long reviewId,
                        String decision, String explanation, long userId) {
        ReviewModels.Review review = reviews.review(organizationId, evaluationId, reviewId)
                .orElseThrow(() -> ApiException.notFound("Revisión"));
        ensureEditable(organizationId, evaluationId);
        if (!List.of("ABIERTA", "EN_REVISION").contains(review.status()))
            throw ApiException.conflict("La revisión ya fue resuelta");
        if (!"APROBADA".equals(decision) && !"ANULADA".equals(decision))
            throw ApiException.badRequest("La decisión debe ser APROBADA o ANULADA");
        reviews.resolve(reviewId, decision, explanation.trim(), userId);
        reviews.setSheetStatus(review.sheetId(), "APROBADA".equals(decision) ? "VALIDADA" : "ANULADA",
                "APROBADA".equals(decision) ? null : explanation.trim());
        exams.recalculateRanking(evaluationId);
        audit.record(organizationId, evaluationId, userId, "REVISION_RESUELTA", "REVISION", reviewId,
                "{\"decision\":\"" + decision + "\"}");
    }

    @Transactional
    public long attach(long organizationId, long evaluationId, long reviewId, MultipartFile file, long userId) {
        ensureEditable(organizationId, evaluationId);
        reviews.review(organizationId, evaluationId, reviewId).orElseThrow(() -> ApiException.notFound("Revisión"));
        if (file == null || file.isEmpty()) throw ApiException.badRequest("Selecciona una imagen");
        if (file.getSize() > maxBytes) throw new ApiException(HttpStatus.PAYLOAD_TOO_LARGE, "FILE_TOO_LARGE", "La evidencia supera el tamaño permitido");
        byte[] bytes;
        try { bytes = file.getBytes(); }
        catch (IOException exception) { throw ApiException.badRequest("No se pudo leer la imagen"); }
        String contentType = detectImageType(bytes);
        String original = file.getOriginalFilename() == null ? "evidencia" : file.getOriginalFilename();
        String filename = original.replace('\\', '/');
        filename = filename.substring(filename.lastIndexOf('/') + 1).replaceAll("[\\p{Cntrl}]", "").trim();
        if (filename.isEmpty()) filename = "evidencia";
        if (filename.length() > 255) filename = filename.substring(0, 255);
        long id = reviews.saveEvidence(reviewId, filename, contentType, bytes, userId);
        audit.record(organizationId, evaluationId, userId, "EVIDENCIA_ADJUNTADA", "EVIDENCIA", id, "{}");
        return id;
    }

    private String detectImageType(byte[] data) {
        if (data.length >= 3 && (data[0] & 0xff) == 0xff && (data[1] & 0xff) == 0xd8 && (data[2] & 0xff) == 0xff)
            return "image/jpeg";
        if (data.length >= 8 && Arrays.equals(Arrays.copyOf(data, 8),
                new byte[]{(byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a})) return "image/png";
        if (data.length >= 12 && new String(data, 0, 4, java.nio.charset.StandardCharsets.US_ASCII).equals("RIFF")
                && new String(data, 8, 4, java.nio.charset.StandardCharsets.US_ASCII).equals("WEBP")) return "image/webp";
        throw ApiException.badRequest("La evidencia debe ser una imagen JPEG, PNG o WebP válida");
    }
}
