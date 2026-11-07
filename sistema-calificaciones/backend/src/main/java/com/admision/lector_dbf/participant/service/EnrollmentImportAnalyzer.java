package com.admision.lector_dbf.participant.service;

import com.admision.lector_dbf.evaluation.model.EvaluationModels.Group;
import com.admision.lector_dbf.evaluation.repository.EvaluationRepository;
import com.admision.lector_dbf.evaluation.repository.RankingPolicyRepository;
import com.admision.lector_dbf.evaluation.service.CsvTableReader;
import com.admision.lector_dbf.participant.model.EnrollmentImportAnalysis;
import com.admision.lector_dbf.participant.model.EnrollmentImportAnalysis.Row;
import com.admision.lector_dbf.participant.model.EnrollmentPlan;
import com.admision.lector_dbf.participant.model.ParticipantModels.*;
import com.admision.lector_dbf.participant.model.ParticipantRequests.SaveEnrollment;
import com.admision.lector_dbf.participant.repository.ParticipantRepository;
import com.admision.lector_dbf.publication.repository.PublicationRepository;
import com.admision.lector_dbf.shared.ApiException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class EnrollmentImportAnalyzer {
    private final CsvTableReader csv;
    private final EvaluationRepository evaluations;
    private final RankingPolicyRepository policies;
    private final PublicationRepository publications;
    private final ParticipantRepository people;
    private final EnrollmentPlanner planner;
    private final ObjectMapper json;
    public EnrollmentImportAnalyzer(CsvTableReader csv, EvaluationRepository evaluations, RankingPolicyRepository policies,
            PublicationRepository publications, ParticipantRepository people, EnrollmentPlanner planner, ObjectMapper json) {
        this.csv = csv; this.evaluations = evaluations; this.policies = policies; this.publications = publications;
        this.people = people; this.planner = planner; this.json = json;
    }

    public EnrollmentImportAnalysis analyze(long org, long evaluation, MultipartFile file, String mode) {
        validateMode(mode);
        var current = evaluations.require(org, evaluation);
        var publication = publications.policy(evaluation);
        if (publication.approvedAt() != null || List.of("PUBLICADA", "ARCHIVADA").contains(current.status()))
            throw ApiException.conflict("Los resultados aprobados o publicados no admiten cambios");
        List<Group> groups = evaluations.groups(evaluation);
        if (mode.equals("GRUPOS") && groups.isEmpty()) throw ApiException.badRequest("Configura los grupos de esta evaluación antes de asignarlos");
        var required = new ArrayList<>(mode.equals("GRUPOS") ? List.of("codigo_evaluacion", "grupo") : List.of("codigo_evaluacion", "nombres"));
        if (!groups.isEmpty() && !required.contains("grupo")) required.add("grupo");
        var allowed = mode.equals("GRUPOS") ? List.of("codigo_evaluacion", "grupo")
                : List.of("codigo_evaluacion", "nombres", "apellidos", "tipo_documento", "documento", "correo", "grupo");
        var table = csv.readMapped(file, required, allowed);
        Set<String> codes = new HashSet<>();
        Set<String> documents = new HashSet<>();
        List<Row> rows = new ArrayList<>();
        for (var source : table.rows()) {
            var values = source.values();
            String code = values.getOrDefault("codigo_evaluacion", "");
            String groupText = values.getOrDefault("grupo", "");
            try {
                if (!codes.add(code)) throw ApiException.badRequest("Código repetido dentro del archivo");
                var existing = people.byCode(org, evaluation, code).orElse(null);
                Long group = resolveGroup(groups, groupText);
                SaveEnrollment input;
                if (mode.equals("GRUPOS")) {
                    if (existing == null) throw ApiException.badRequest("No hay una inscripción con ese código en esta evaluación");
                    input = new SaveEnrollment(code, existing.personId(), existing.names(), existing.surnames(),
                            existing.documentType(), existing.document(), existing.email(), group);
                } else input = new SaveEnrollment(code, null, values.get("nombres"), values.get("apellidos"),
                        values.get("tipo_documento"), values.get("documento"), values.get("correo"), group);
                input = planner.normalize(input, groups);
                if (!input.document().isBlank() && !documents.add(input.documentType() + ":" + EnrollmentPlanner.documentKey(input.document())))
                    throw ApiException.badRequest("El mismo documento aparece en más de una fila. Una persona solo puede tener una inscripción en esta evaluación");
                var plan = planner.plan(org, evaluation, input, existing, true);
                String action = existing == null ? "CREADO" : plan.completed() ? "COMPLETADO" : plan.groupChanged() ? "GRUPO_ACTUALIZADO" : "CONSERVADO";
                String detail = existing == null ? "Alta de inscripción. " : "Conservación de la inscripción y sus exámenes. ";
                detail += plan.createPerson() ? "Alta de datos personales." : plan.completePerson() ? "Completar el registro provisional."
                        : plan.reused() ? "Persona reutilizada: " + plan.person().names() + " " + EnrollmentPlanner.clean(plan.person().surnames()) + ". Sus datos personales se conservan."
                        : "Datos personales registrados conservados.";
                if (plan.groupChanged()) detail += " Asignación del grupo indicado y actualización del ranking.";
                String resultingName = plan.createPerson() || plan.completePerson()
                        ? input.names() + " " + input.surnames() : plan.person().names() + " " + EnrollmentPlanner.clean(plan.person().surnames());
                detail = resultingName.trim() + ": " + detail;
                rows.add(new Row(new ImportRow(source.row(), code, action, detail,
                        existing == null ? null : existing.groupName(), groupName(groups, group)), plan));
            } catch (ApiException error) {
                rows.add(new Row(new ImportRow(source.row(), code, "ERROR", error.getMessage(), null, groupText), null));
            }
        }
        ImportCounts counts = counts(rows);
        String fingerprint = fingerprint(List.of(org, evaluation, mode, current, publication, groups,
                policies.latest(evaluation).orElseThrow(), rows));
        return new EnrollmentImportAnalysis(counts, List.copyOf(rows), fingerprint);
    }

    public static void validateMode(String mode) {
        if (!List.of("REGISTROS", "GRUPOS").contains(mode)) throw ApiException.badRequest("Modo de importación inválido");
    }
    private Long resolveGroup(List<Group> groups, String value) {
        if (value.isBlank()) return null;
        var exact = groups.stream().filter(group -> group.code().equals(value)).toList();
        if (exact.size() == 1) return exact.get(0).id();
        var codes = groups.stream().filter(group -> group.code().equalsIgnoreCase(value)).toList();
        if (codes.size() == 1) return codes.get(0).id();
        if (codes.size() > 1) throw ApiException.badRequest("Código de grupo ambiguo. Respeta el código exacto de la lista");
        var names = groups.stream().filter(group -> group.name().replaceAll("\\s+", " ").equalsIgnoreCase(value.replaceAll("\\s+", " "))).toList();
        if (names.size() != 1) throw ApiException.badRequest("El grupo no existe o su nombre es ambiguo. Utiliza su código de la lista de esta evaluación");
        return names.get(0).id();
    }
    private String groupName(List<Group> groups, Long id) {
        return id == null ? null : groups.stream().filter(group -> group.id() == id).findFirst().orElseThrow().name();
    }
    private ImportCounts counts(List<Row> rows) {
        int created = 0, peopleCreated = 0, completed = 0, reused = 0, preserved = 0, changes = 0, errors = 0;
        for (var row : rows) {
            EnrollmentPlan plan = row.plan();
            if (plan == null) { errors++; continue; }
            if (plan.existing() == null) created++;
            if (plan.createPerson()) peopleCreated++;
            if (plan.completed()) completed++;
            if (plan.reused()) reused++;
            if (plan.existing() != null && !plan.existing().automatic()) preserved++;
            if (plan.groupChanged()) changes++;
        }
        return new ImportCounts(rows.size(), created, peopleCreated, completed, reused, preserved, changes, errors);
    }
    private String fingerprint(Object snapshot) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(json.writeValueAsBytes(snapshot))); }
        catch (NoSuchAlgorithmException | JsonProcessingException error) { throw new IllegalStateException("No se pudo validar la vista previa", error); }
    }
}
