package com.admision.lector_dbf.publication.service;

import com.admision.lector_dbf.audit.repository.AuditRepository;
import com.admision.lector_dbf.evaluation.repository.EvaluationRepository;
import com.admision.lector_dbf.evidence.repository.ReviewRepository;
import com.admision.lector_dbf.publication.model.PublicationModels.*;
import com.admision.lector_dbf.publication.model.PublicationRequests.UpdatePolicy;
import com.admision.lector_dbf.publication.repository.PublicationRepository;
import com.admision.lector_dbf.security.OrganizationAccess;
import com.admision.lector_dbf.shared.ApiException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

@Service
public class PublicationService {
    private final PublicationRepository publications;
    private final EvaluationRepository evaluations;
    private final ReviewRepository reviews;
    private final AuditRepository audit;
    private final OrganizationAccess access;
    private final SecureRandom random = new SecureRandom();

    public PublicationService(PublicationRepository publications, EvaluationRepository evaluations,
            ReviewRepository reviews, AuditRepository audit, OrganizationAccess access) {
        this.publications = publications; this.evaluations = evaluations; this.reviews = reviews;
        this.audit = audit; this.access = access;
    }

    public Policy policy(long org, long evaluation, Authentication auth) {
        access.require(org, auth);
        evaluations.require(org, evaluation);
        return publications.policy(evaluation);
    }

    @Transactional
    public Policy save(long org, long evaluation, UpdatePolicy request, Authentication auth) {
        long user = access.require(org, auth, "ADMINISTRADOR").id();
        publications.lock(org, evaluation);
        String currentStatus = evaluations.require(org, evaluation).status();
        if ("CERRADA".equals(currentStatus) && publications.policy(evaluation).approvedAt() != null)
            throw ApiException.conflict("La consulta ya fue aprobada. Publica los resultados antes de ajustar sus campos visibles");
        if (!java.util.Set.of("CERRADA", "PUBLICADA").contains(currentStatus))
            throw ApiException.conflict("La configuración de consulta requiere una evaluación cerrada o publicada");
        if (request.visibleFrom() != null && request.visibleUntil() != null
                && !request.visibleUntil().isAfter(request.visibleFrom()))
            throw ApiException.badRequest("La fecha final debe ser posterior a la inicial");
        publications.save(evaluation, request.showScore(), request.showPosition(), request.showPassed(),
                request.showCounts(), request.visibleFrom(), request.visibleUntil());
        audit.record(org, evaluation, user, "PUBLICACION_CONFIGURADA", "EVALUACION", evaluation, "{}");
        return publications.policy(evaluation);
    }

    @Transactional
    public Policy approve(long org, long evaluation, Authentication auth) {
        long user = access.require(org, auth, "ADMINISTRADOR").id();
        publications.lock(org, evaluation);
        if (!"CERRADA".equals(evaluations.require(org, evaluation).status()))
            throw ApiException.conflict("Solo se puede aprobar una evaluación cerrada");
        if (!publications.policy(evaluation).configured())
            throw ApiException.conflict("Configura los datos y el periodo de consulta antes de aprobar");
        if (reviews.pending(evaluation) > 0 || publications.hasBlockingSheets(evaluation))
            throw ApiException.conflict("Resuelve todos los casos pendientes y exámenes observados antes de aprobar");
        if (publications.hasUnfinishedBatches(evaluation))
            throw ApiException.conflict("Termina las importaciones pendientes antes de aprobar");
        if (publications.validResults(evaluation) == 0)
            throw ApiException.conflict("La evaluación no tiene resultados válidos para publicar");
        if (publications.hasStaleResults(evaluation))
            throw ApiException.conflict("Hay resultados calculados con versiones anteriores; recalcula antes de aprobar");
        if (publications.ungroupedResults(evaluation) > 0)
            throw ApiException.conflict("Asigna un grupo a todos los exámenes válidos antes de aprobar el ranking por grupo");
        publications.approve(evaluation, user);
        audit.record(org, evaluation, user, "RESULTADOS_APROBADOS", "EVALUACION", evaluation, "{}");
        return publications.policy(evaluation);
    }

    @Transactional
    public Policy publish(long org, long evaluation, Authentication auth) {
        long user = access.require(org, auth, "ADMINISTRADOR").id();
        publications.lock(org, evaluation);
        if (!"CERRADA".equals(evaluations.require(org, evaluation).status()))
            throw ApiException.conflict("Solo se puede publicar una evaluación cerrada");
        if (publications.policy(evaluation).approvedAt() == null)
            throw ApiException.conflict("Un administrador debe aprobar los resultados antes de publicarlos");
        if (reviews.pending(evaluation) > 0 || publications.hasBlockingSheets(evaluation))
            throw ApiException.conflict("Hay casos pendientes de resolver");
        if (publications.ungroupedResults(evaluation) > 0)
            throw ApiException.conflict("Hay exámenes válidos sin grupo asignado");
        publications.publish(evaluation, user);
        audit.record(org, evaluation, user, "RESULTADOS_PUBLICADOS", "EVALUACION", evaluation, "{}");
        return publications.policy(evaluation);
    }

    @Transactional
    public byte[] exportAccess(long org, long evaluation, Authentication auth) {
        long user = access.require(org, auth, "ADMINISTRADOR").id();
        publications.lock(org, evaluation);
        String status = evaluations.require(org, evaluation).status();
        if (!"PUBLICADA".equals(status))
            throw ApiException.conflict("Publica la evaluación antes de generar los accesos");
        var candidates = publications.candidates(evaluation);
        StringBuilder csv = new StringBuilder("codigo_participante;codigo_acceso;nombre;correo\r\n");
        for (AccessCandidate candidate : candidates) {
            byte[] bytes = new byte[32];
            random.nextBytes(bytes);
            String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
            publications.saveAccess(candidate.registrationId(), hash(token), user);
            String code = candidate.participantCode().replaceAll("[\\p{Cntrl}]", " ").trim();
            if (code.matches("^[=+@-].*")) code = "'" + code;
            csv.append(csvText(code)).append(';').append(token).append(';')
                    .append(csvText(candidate.participantName())).append(';').append(csvText(candidate.email())).append("\r\n");
        }
        audit.record(org, evaluation, user, "ACCESOS_GENERADOS", "EVALUACION", evaluation,
                "{\"cantidad\":" + candidates.size() + "}");
        return csv.toString().getBytes(StandardCharsets.UTF_8);
    }

    public PublicResult lookup(String token) {
        if (token == null || !token.matches("[A-Za-z0-9_-]{43}"))
            throw ApiException.notFound("Resultado disponible");
        return publications.lookup(hash(token))
                .orElseThrow(() -> ApiException.notFound("Resultado disponible"));
    }

    @Transactional(readOnly=true,isolation=org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
    public ReportContext report(String token) {
        PublicResult result=lookup(token);
        return publications.reportContext(hash(token),result);
    }

    private String csvText(String value) {
        String text=value==null ? "" : value.replaceAll("[\\p{Cntrl}]"," ");
        if (text.stripLeading().matches("^[=+@-].*")) text="'"+text;
        return "\""+text.replace("\"","\"\"")+"\"";
    }

    private String hash(String token) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(token.getBytes(StandardCharsets.US_ASCII)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 no está disponible", exception);
        }
    }
}
