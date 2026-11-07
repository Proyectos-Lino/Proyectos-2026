package com.admision.lector_dbf.evaluation.service;

import com.admision.lector_dbf.evaluation.model.BulkImportSummary;
import com.admision.lector_dbf.evaluation.model.EvaluationModels.Group;
import com.admision.lector_dbf.evaluation.model.EvaluationRequests.CreateParticipant;
import jakarta.validation.Validator;
import com.admision.lector_dbf.evaluation.repository.ParticipantImportReportRepository;
import com.admision.lector_dbf.evaluation.repository.EvaluationRepository;
import com.admision.lector_dbf.publication.repository.PublicationRepository;
import com.admision.lector_dbf.security.OrganizationAccess;
import com.admision.lector_dbf.shared.ApiException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class BulkImportService {
    private final EvaluationRepository repository;
    private final OrganizationAccess access;
    private final CsvTableReader csv;
    private final PublicationRepository publications;
    private final Validator validator;
    private final ParticipantImportReportRepository reports;

    public BulkImportService(EvaluationRepository repository, OrganizationAccess access, CsvTableReader csv,
                             PublicationRepository publications,Validator validator,ParticipantImportReportRepository reports) {
        this.repository = repository; this.access = access; this.csv = csv; this.publications = publications;
        this.validator=validator;
        this.reports=reports;
    }

    @Transactional
    public BulkImportSummary participants(long organizationId, MultipartFile file, Authentication auth) {
        long actor=access.require(organizationId, auth, "ADMINISTRADOR", "OPERADOR").id();
        List<List<String>> rows = csv.read(file, List.of("codigo", "nombres", "apellidos", "documento"),"correo");
        Set<String> codes = new HashSet<>();
        for (int i = 0; i < rows.size(); i++) {
            List<String> row = rows.get(i);
            if (!validator.validate(new CreateParticipant(row.get(0),row.get(1),row.get(2),row.get(3),row.get(4))).isEmpty())
                throw ApiException.badRequest("Fila " + (i+2) + ": revisa nombres, código, correo y longitudes");
            if (!codes.add(row.get(0))) throw ApiException.badRequest("Código repetido en el CSV: " + row.get(0));
        }
        int created = 0;
        int updated = 0;
        long report=reports.create(organizationId,actor);
        int line=2;
        for (List<String> row : rows) {
            var result=repository.registerParticipant(organizationId,row.get(0),row.get(1),row.get(2),row.get(3),row.get(4));
            if (result.isPresent()) { if (result.get().created()) created++; else updated++; }
            reports.row(report,line++,row.get(0),result.map(write -> write.created()?"CREADO":"COMPLETADO").orElse("CONSERVADO"));
        }
        return new BulkImportSummary(created,updated, rows.size() - created - updated,report);
    }

    @Transactional
    public BulkImportSummary groups(long organizationId, long evaluationId, MultipartFile file, Authentication auth) {
        access.require(organizationId, auth, "ADMINISTRADOR", "OPERADOR");
        publications.assertMutable(organizationId, evaluationId);
        repository.require(organizationId, evaluationId);
        List<List<String>> rows = csv.read(file, List.of("codigo", "nombre", "vacantes"));
        Set<String> codes = new HashSet<>();
        for (int i = 0; i < rows.size(); i++) {
            List<String> row = rows.get(i);
            if (row.get(0).isBlank() || row.get(1).isBlank() || row.get(0).length() > 60 || row.get(1).length() > 180)
                throw ApiException.badRequest("Fila " + (i + 2) + ": revisa código y nombre");
            if (!row.get(2).isBlank()) capacity(row.get(2), i + 2);
            if (!codes.add(row.get(0))) throw ApiException.badRequest("Código repetido en el CSV: " + row.get(0));
        }
        Set<String> existing = new HashSet<>(repository.groups(evaluationId).stream().map(Group::code).toList());
        int created = 0;
        for (List<String> row : rows) {
            if (existing.contains(row.get(0))) continue;
            repository.createGroup(evaluationId, row.get(0), row.get(1),
                    row.get(2).isBlank() ? null : capacity(row.get(2), 0));
            created++;
        }
        return new BulkImportSummary(created, rows.size() - created);
    }

    private int capacity(String value, int row) {
        try {
            int number = Integer.parseInt(value);
            if (number >= 0) return number;
        } catch (NumberFormatException ignored) { }
        throw ApiException.badRequest("Fila " + row + ": vacantes debe ser un entero no negativo");
    }
}
