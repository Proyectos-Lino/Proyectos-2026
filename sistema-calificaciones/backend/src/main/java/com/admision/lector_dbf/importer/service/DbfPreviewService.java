package com.admision.lector_dbf.importer.service;

import com.admision.lector_dbf.evaluation.model.EvaluationModels.Form;
import com.admision.lector_dbf.evaluation.repository.EvaluationRepository;
import com.admision.lector_dbf.evaluation.repository.RankingPolicyRepository;
import com.admision.lector_dbf.evaluation.repository.SectionConfigurationRepository;
import com.admision.lector_dbf.importer.model.DbfPreviewModels.*;
import com.admision.lector_dbf.importer.model.ImportModels.*;
import com.admision.lector_dbf.importer.reader.DbfParser;
import com.admision.lector_dbf.importer.repository.ImportRepository;
import com.admision.lector_dbf.importer.repository.ImportRepository.ExistingData;
import com.admision.lector_dbf.security.OrganizationAccess;
import com.admision.lector_dbf.shared.ApiException;
import com.admision.lector_dbf.shared.PageRequest;
import com.admision.lector_dbf.shared.PageResult;
import com.fasterxml.jackson.core.JsonProcessingException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.HexFormat;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class DbfPreviewService {
    private final DbfParser parser;
    private final EvaluationRepository evaluations;
    private final RankingPolicyRepository policies;
    private final SectionConfigurationRepository sections;
    private final ImportRepository imports;
    private final OmrImportService processor;
    private final OrganizationAccess access;

    public DbfPreviewService(DbfParser parser, EvaluationRepository evaluations,
                             RankingPolicyRepository policies, SectionConfigurationRepository sections,
                             ImportRepository imports, OmrImportService processor, OrganizationAccess access) {
        this.parser = parser;
        this.evaluations = evaluations;
        this.policies = policies;
        this.sections = sections;
        this.imports = imports;
        this.processor = processor;
        this.access = access;
    }

    public Inspection inspect(long organizationId, long evaluationId, MultipartFile identification,
                              MultipartFile responses, MultipartFile keys, Authentication auth) {
        access.require(organizationId, auth, "ADMINISTRADOR", "OPERADOR");
        evaluations.require(organizationId, evaluationId);
        return parser.inspect(parser.readFiles(identification, responses, keys));
    }

    public Preview preview(long organizationId, long evaluationId, MultipartFile identification,
                           MultipartFile responses, MultipartFile keys, ColumnMapping mapping, Options options,
                           String filter, int page, int size, Authentication auth) {
        access.require(organizationId, auth, "ADMINISTRADOR", "OPERADOR");
        new PageRequest(page, size);
        if (!Set.of("TODOS", "ACEPTAR", "OBSERVADA", "CORREGIR").contains(filter))
            throw ApiException.badRequest("Filtro de vista previa inválido");
        DbfFiles files = parser.readFiles(identification, responses, keys);
        return analyze(organizationId, evaluationId, identification, responses, keys,
                files, mapping, options, filter, page, size);
    }

    @Transactional
    public ImportSummary confirm(long organizationId, long evaluationId, MultipartFile identification,
                                 MultipartFile responses, MultipartFile keys, ColumnMapping mapping,
                                 Options options, String expectedFingerprint, Authentication auth)
            throws JsonProcessingException {
        long userId = access.require(organizationId, auth, "ADMINISTRADOR", "OPERADOR").id();
        evaluations.require(organizationId, evaluationId);
        imports.lockEvaluation(evaluationId);
        DbfFiles files = parser.readFiles(identification, responses, keys);
        Preview current = analyze(organizationId, evaluationId, identification, responses, keys,
                files, mapping, options, "TODOS", 0, 1);
        if (expectedFingerprint == null || !expectedFingerprint.equals(current.fingerprint()))
            throw ApiException.conflict("La vista previa ya no coincide con los archivos o la configuración. Analiza nuevamente");
        if (!current.generalIssues().isEmpty() || current.correctionNeeded() > 0)
            throw ApiException.conflict("Corrige las incidencias antes de confirmar la importación");
        if ("GUIADA".equals(options.mode()))
            return processor.prepareAndUploadMapped(organizationId, evaluationId, userId, options,
                    files, mapping, responses.getOriginalFilename());
        return processor.uploadMapped(organizationId, evaluationId, userId,
                files, mapping, responses.getOriginalFilename());
    }

    private Preview analyze(long org, long evaluationId, MultipartFile identification,
                            MultipartFile responses, MultipartFile keys, DbfFiles files,
                            ColumnMapping mapping, Options options, String filter, int page, int size) {
        validateOptions(options);
        var evaluation = evaluations.require(org, evaluationId);
        boolean guided = "GUIADA".equals(options.mode());
        MappedRows mapped = parser.map(files, mapping);
        List<String> general = new ArrayList<>();
        if (mapped.identifications().isEmpty()) general.add("IDENTIFI no tiene registros");
        if (mapped.responses().isEmpty()) general.add("RESPUEST no tiene registros");
        if (mapped.keys().isEmpty()) general.add("CLAVES no tiene registros");
        if (guided && !Set.of("BORRADOR", "CONFIGURADA", "EN_PROCESO").contains(evaluation.status()))
            general.add("Esta evaluación ya no admite una carga guiada");
        if (!guided && !"EN_PROCESO".equals(evaluation.status()))
            general.add("La evaluación debe estar en proceso para importar sin preparación");
        if (!guided && evaluations.latestRule(evaluationId).isEmpty())
            general.add("Configura una regla de calificación antes de importar");
        long policyId = policies.latest(evaluationId).map(policy -> policy.id()).orElse(0L);
        if (policyId == 0) general.add("Configura la política de resultados antes de importar");

        Map<String, Identification> identities = new HashMap<>();
        for (IdentificationRow row : mapped.identifications()) {
            Identification identity = row.value();
            if (identity.sheetCode().isBlank() || identity.participantCode().isBlank() || identity.formCode().isBlank())
                general.add("IDENTIFI fila " + row.number() + ": falta hoja, tema o participante");
            else if (identities.putIfAbsent(identity.sheetCode(), identity) != null)
                general.add("IDENTIFI fila " + row.number() + ": código de hoja duplicado " + identity.sheetCode());
        }
        Map<String, Key> answerKeys = new HashMap<>();
        for (KeyRow row : mapped.keys()) {
            Key key = row.value();
            if (key.formCode().isBlank()) general.add("CLAVES fila " + row.number() + ": falta el tema");
            else if (answerKeys.putIfAbsent(key.formCode().toUpperCase(), key) != null)
                general.add("CLAVES fila " + row.number() + ": tema duplicado " + key.formCode());
        }
        Map<String, Form> forms = new HashMap<>();
        StringBuilder configuration = new StringBuilder(evaluation.status()).append(':').append(policyId);
        evaluations.latestRule(evaluationId).ifPresent(rule -> configuration.append(':').append(rule.id()));
        for (Form form : evaluations.forms(evaluationId)) {
            forms.put(form.code().toUpperCase(), form);
            configuration.append(':').append(form.id()).append('/').append(form.questions())
                    .append('/').append(form.options()).append('/');
            sections.latest(form.id()).ifPresent(section -> configuration.append(section.id()));
        }
        for (KeyRow row : mapped.keys()) {
            Key key = row.value();
            Form form = forms.get(key.formCode().toUpperCase());
            if (!guided && form == null) continue;
            int questions = form == null ? options.questions() : form.questions();
            int alternatives = form == null ? options.options() : form.options();
            if (key.answers().size() < questions)
                general.add("CLAVES fila " + row.number() + ": menos de " + questions + " respuestas para " + key.formCode());
            else if (!validAnswers(key.answers(), questions, alternatives, false))
                general.add("CLAVES fila " + row.number() + ": opciones inválidas para " + key.formCode());
        }
        ExistingData existing = imports.existingData(evaluationId);
        Set<String> seenSheets = new HashSet<>();
        Set<String> seenParticipants = new HashSet<>();
        List<PreviewRow> rows = new ArrayList<>();
        int accepted = 0, observed = 0, correction = 0;
        for (ResponseRow source : mapped.responses()) {
            Response response = source.value();
            Identification identity = identities.get(response.sheetCode());
            String participant = identity == null ? "" : identity.participantCode();
            String formCode = identity == null ? response.formCode() : identity.formCode();
            List<String> issues = new ArrayList<>();
            if (response.sheetCode().isBlank()) issues.add("Falta el código de hoja");
            else if (!seenSheets.add(response.sheetCode())) issues.add("Hoja repetida en RESPUEST");
            if (identity == null) issues.add("No tiene identificación en IDENTIFI");
            if (identity != null && !participant.isBlank() && !seenParticipants.add(participant))
                issues.add("El participante aparece en otra hoja del archivo");
            if (existing.sheetCodes().contains(response.sheetCode())) issues.add("La hoja ya fue importada");
            if (!participant.isBlank() && existing.examinedParticipants().contains(participant))
                issues.add("El participante ya tiene un examen en esta evaluación");
            if (!guided && !participant.isBlank() && !existing.registeredParticipants().contains(participant))
                issues.add("El participante no está inscrito en la evaluación");
            Form form = forms.get(formCode.toUpperCase());
            Key key = answerKeys.get(formCode.toUpperCase());
            if (key == null) issues.add("El tema no tiene clave en CLAVES");
            if (!guided && form == null) issues.add("El tema no está configurado en la evaluación");
            int questions = form == null ? options.questions() : form.questions();
            int alternatives = form == null ? options.options() : form.options();
            if (key != null && key.answers().size() < questions)
                issues.add("La clave tiene menos de " + questions + " respuestas");
            else if (key != null && !validAnswers(key.answers(), questions, alternatives, false))
                issues.add("La clave contiene opciones no permitidas para el tema");
            if (response.answers().size() < questions)
                issues.add("La hoja tiene menos de " + questions + " respuestas");
            else if (!validAnswers(response.answers(), questions, alternatives, true))
                issues.add("La hoja contiene opciones no permitidas para el tema");
            String decision;
            if (!issues.isEmpty()) { decision = "CORREGIR"; correction++; }
            else if (response.formCode().isBlank() || !response.formCode().equalsIgnoreCase(formCode)) {
                issues.add(response.formCode().isBlank() ? "La hoja no indica el tema" : "El tema de la hoja no coincide con IDENTIFI");
                decision = "OBSERVADA"; observed++;
            } else { decision = "ACEPTAR"; accepted++; }
            rows.add(new PreviewRow(source.number(), response.sheetCode(), participant, formCode, decision, issues));
        }
        String fingerprint = fingerprint(identification, responses, keys, mapping, options,
                configuration.toString(), rows, general);
        List<PreviewRow> selected = rows.stream().filter(row -> "TODOS".equals(filter) || filter.equals(row.decision())).toList();
        int from = Math.min(page * size, selected.size());
        List<PreviewRow> visible = selected.subList(from, Math.min(from + size, selected.size()));
        return new Preview(fingerprint, rows.size(), accepted, observed, correction, general,
                PageResult.of(visible, page, size, selected.size()));
    }

    private void validateOptions(Options options) {
        if (options == null || options.mode() == null || !Set.of("GUIADA", "CONFIGURADA").contains(options.mode()))
            throw ApiException.badRequest("Modo de carga inválido");
        if ("GUIADA".equals(options.mode()) && (options.questions() < 1 || options.options() < 2 || options.options() > 26
                || options.correct() == null || options.correct().signum() < 0
                || options.incorrect() == null || options.blank() == null))
            throw ApiException.badRequest("Revisa las preguntas, opciones y puntajes de la carga");
    }

    private boolean validAnswers(List<String> answers, int questions, int alternatives, boolean allowMark) {
        for (int index = 0; index < questions; index++) {
            String answer = answers.get(index);
            if (answer.isBlank() || (allowMark && "*".equals(answer))) continue;
            if (answer.length() != 1 || answer.charAt(0) < 'A' || answer.charAt(0) >= 'A' + alternatives)
                return false;
        }
        return true;
    }

    private String fingerprint(MultipartFile identification, MultipartFile responses, MultipartFile keys,
                               ColumnMapping mapping, Options options, String configuration,
                               List<PreviewRow> rows, List<String> general) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            for (MultipartFile file : List.of(identification, responses, keys)) digest.update(file.getBytes());
            digest.update((mapping.toString() + options + configuration + rows + general)
                    .getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest.digest());
        } catch (IOException | NoSuchAlgorithmException exception) {
            throw ApiException.badRequest("No se pudo comprobar la vista previa de los archivos DBF");
        }
    }
}
