package com.admision.lector_dbf.participant.service;

import com.admision.lector_dbf.audit.repository.AuditRepository;
import com.admision.lector_dbf.evaluation.repository.EvaluationRepository;
import com.admision.lector_dbf.exam.repository.ExamRepository;
import com.admision.lector_dbf.participant.model.ParticipantModels.*;
import com.admision.lector_dbf.participant.model.EnrollmentImportAnalysis;
import com.admision.lector_dbf.participant.repository.EnrollmentImportRepository;
import com.admision.lector_dbf.report.service.CsvReportWriter;
import com.admision.lector_dbf.security.OrganizationAccess;
import com.admision.lector_dbf.shared.*;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class EnrollmentImportService {
    private final EnrollmentImportAnalyzer analyzer;
    private final EnrollmentPlanner planner;
    private final ParticipantService participants;
    private final EnrollmentImportRepository reports;
    private final EvaluationRepository evaluations;
    private final ExamRepository exams;
    private final AuditRepository audit;
    private final OrganizationAccess access;
    public EnrollmentImportService(EnrollmentImportAnalyzer analyzer, EnrollmentPlanner planner, ParticipantService participants,
            EnrollmentImportRepository reports, EvaluationRepository evaluations, ExamRepository exams, AuditRepository audit, OrganizationAccess access) {
        this.analyzer = analyzer; this.planner = planner; this.participants = participants; this.reports = reports;
        this.evaluations = evaluations; this.exams = exams; this.audit = audit; this.access = access;
    }

    @Transactional(readOnly=true, isolation=Isolation.REPEATABLE_READ)
    public Preview preview(long org, long evaluation, MultipartFile file, String mode, String query, String action,
            int page, int size, Authentication auth) {
        access.require(org, auth, "ADMINISTRADOR", "OPERADOR"); new PageRequest(page, size); validateAction(action);
        var analysis = analyzer.analyze(org, evaluation, file, mode);
        String search = query.trim().toLowerCase(Locale.ROOT);
        var filtered = analysis.rows().stream().map(EnrollmentImportAnalysis.Row::display)
                .filter(row -> action.isEmpty() || row.action().equals(action))
                .filter(row -> (row.code() + " " + row.detail() + " " + row.group()).toLowerCase(Locale.ROOT).contains(search)).toList();
        var items = filtered.stream().skip((long) page * size).limit(size).toList();
        return new Preview(analysis.counts(), PageResult.of(items, page, size, filtered.size()), analysis.fingerprint());
    }

    @Transactional
    public Imported confirm(long org, long evaluation, MultipartFile file, String mode, String fingerprint, Authentication auth) {
        long actor = participants.lock(org, evaluation, auth);
        var analysis = analyzer.analyze(org, evaluation, file, mode);
        if (analysis.counts().errors() > 0) throw ApiException.conflict("Corrige todas las filas con problemas antes de confirmar");
        if (fingerprint == null || !analysis.fingerprint().equals(fingerprint))
            throw ApiException.conflict("Los datos cambiaron desde la vista previa. Analiza el archivo otra vez antes de confirmar");
        long report = reports.create(org, evaluation, actor, mode);
        for (var row : analysis.rows()) {
            long registration = planner.apply(org, evaluation, row.plan());
            reports.saveRow(report, row.display());
            audit.record(org, evaluation, actor, "PARTICIPANTE_IMPORTADO", "INSCRIPCION", registration,
                    Map.of("codigo", row.display().code(), "informe", report, "fila", row.display().row(),
                            "grupoAnterior", row.display().previousGroup() == null ? "" : row.display().previousGroup(),
                            "grupoNuevo", row.display().group() == null ? "" : row.display().group()));
        }
        // Una sola actualización de ranking para toda la carga, dentro de la misma transacción.
        exams.recalculateRanking(evaluation);
        audit.record(org, evaluation, actor, "INSCRIPCIONES_IMPORTADAS", "EVALUACION", evaluation,
                Map.of("informe", report, "filas", analysis.counts().total(), "modo", mode));
        return new Imported(report, analysis.counts());
    }

    public byte[] template(long org, long evaluation, String mode, Authentication auth) {
        access.require(org, auth); evaluations.require(org, evaluation); EnrollmentImportAnalyzer.validateMode(mode);
        boolean grouped = !evaluations.groups(evaluation).isEmpty();
        if (mode.equals("GRUPOS") && !grouped) throw ApiException.badRequest("La evaluación no tiene grupos configurados");
        String header = mode.equals("GRUPOS") ? "codigo_evaluacion;grupo"
                : "codigo_evaluacion;nombres;apellidos;tipo_documento;documento;correo" + (grouped ? ";grupo" : "");
        return ("\uFEFF" + header + "\r\n").getBytes(StandardCharsets.UTF_8);
    }

    public PageResult<ImportRow> report(long org, long evaluation, long report, String query, String action, int page, int size, Authentication auth) {
        access.require(org, auth); evaluations.require(org, evaluation); new PageRequest(page, size); validateAction(action);
        return reports.page(org, evaluation, report, query, action, page, size);
    }

    public byte[] reportCsv(long org, long evaluation, long report, Authentication auth) {
        access.require(org, auth); evaluations.require(org, evaluation);
        StringWriter text = new StringWriter(); text.append('\uFEFF');
        CsvReportWriter csv = new CsvReportWriter(text);
        csv.row("Fila", "Código de evaluación", "Acción", "Detalle", "Grupo anterior", "Grupo asignado");
        for (var row : reports.page(org, evaluation, report, "", "", 0, 5000).items())
            csv.row(row.row(), row.code(), row.action(), row.detail(), row.previousGroup(), row.group());
        return text.toString().getBytes(StandardCharsets.UTF_8);
    }

    private void validateAction(String action) {
        if (!List.of("", "ERROR", "CREADO", "COMPLETADO", "GRUPO_ACTUALIZADO", "CONSERVADO").contains(action))
            throw ApiException.badRequest("Filtro de resultado inválido");
    }
}
