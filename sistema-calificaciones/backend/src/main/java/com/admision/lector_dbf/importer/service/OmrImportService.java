package com.admision.lector_dbf.importer.service;

import com.admision.lector_dbf.evaluation.model.EvaluationModels;
import com.admision.lector_dbf.evaluation.model.ScoringModels.SectionConfiguration;
import com.admision.lector_dbf.evaluation.repository.EvaluationRepository;
import com.admision.lector_dbf.evaluation.repository.RankingPolicyRepository;
import com.admision.lector_dbf.evaluation.repository.SectionConfigurationRepository;
import com.admision.lector_dbf.exam.repository.ExamRepository;
import com.admision.lector_dbf.evidence.repository.ReviewRepository;
import com.admision.lector_dbf.audit.repository.AuditRepository;
import com.admision.lector_dbf.importer.model.DbfPreviewModels.ColumnMapping;
import com.admision.lector_dbf.importer.model.DbfPreviewModels.DbfFiles;
import com.admision.lector_dbf.importer.model.DbfPreviewModels.Options;
import com.admision.lector_dbf.importer.model.ImportModels;
import com.admision.lector_dbf.importer.model.ImportModels.*;
import com.admision.lector_dbf.importer.reader.DbfParser;
import com.admision.lector_dbf.importer.repository.ImportRepository;
import com.admision.lector_dbf.security.OrganizationAccess;
import com.admision.lector_dbf.shared.ApiException;
import com.admision.lector_dbf.shared.PageRequest;
import com.admision.lector_dbf.shared.PageResult;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.math.BigDecimal;

@Service
public class OmrImportService {
    private final DbfParser parser;
    private final ScoringService scoring;
    private final EvaluationRepository evaluations;
    private final ImportRepository imports;
    private final ExamRepository exams;
    private final ObjectMapper json;
    private final OrganizationAccess access;
    private final SectionConfigurationRepository sectionConfigurations;
    private final RankingPolicyRepository policies;
    private final ReviewRepository reviews;
    private final AuditRepository audit;

    public OmrImportService(DbfParser parser, ScoringService scoring, EvaluationRepository evaluations,
                            ImportRepository imports, ExamRepository exams, ObjectMapper json, OrganizationAccess access,
                            SectionConfigurationRepository sectionConfigurations, RankingPolicyRepository policies,
                            ReviewRepository reviews, AuditRepository audit) {
        this.parser = parser;
        this.scoring = scoring;
        this.evaluations = evaluations;
        this.imports = imports;
        this.exams = exams;
        this.json = json;
        this.access = access;
        this.sectionConfigurations = sectionConfigurations;
        this.policies = policies;
        this.reviews = reviews;
        this.audit = audit;
    }

    public List<ImportModels.Batch> batches(long organizationId, long evaluationId, Authentication auth) {
        access.require(organizationId, auth);
        evaluations.require(organizationId, evaluationId);
        return imports.batches(evaluationId);
    }

    public PageResult<ImportModels.Batch> batchesPage(long organizationId, long evaluationId,
                                                       String query, String status, int page, int size, Authentication auth) {
        access.require(organizationId, auth);
        evaluations.require(organizationId, evaluationId);
        new PageRequest(page, size);
        if (!status.isEmpty() && !List.of("PENDIENTE", "PROCESANDO", "COMPLETADO",
                "COMPLETADO_CON_ERRORES", "FALLIDO").contains(status))
            throw ApiException.badRequest("Estado inválido");
        return imports.batchesPage(evaluationId, query, status, page, size);
    }

    @Transactional
    public ImportSummary upload(long organizationId, long evaluationId, MultipartFile identification,
            MultipartFile responses, MultipartFile keys, Authentication auth) throws JsonProcessingException {
        long userId = access.require(organizationId, auth, "ADMINISTRADOR", "OPERADOR").id();
        return importFiles(organizationId, evaluationId, userId, identification, responses, keys);
    }

    @Transactional
    public ImportSummary prepareAndUpload(long organizationId, long evaluationId, int questions, int options,
            BigDecimal correct, BigDecimal incorrect, BigDecimal blank, BigDecimal passingScore,
            MultipartFile identification, MultipartFile responses, MultipartFile keys,
            Authentication auth) throws JsonProcessingException {
        long userId = access.require(organizationId, auth, "ADMINISTRADOR", "OPERADOR").id();
        return prepareParsed(organizationId, evaluationId, userId, questions, options, correct,
                incorrect, blank, passingScore, parser.parse(identification, responses, keys),
                responses.getOriginalFilename());
    }

    @Transactional
    public ImportSummary prepareAndUploadMapped(long organizationId, long evaluationId, long userId,
            Options options, DbfFiles files, ColumnMapping mapping, String responseFilename)
            throws JsonProcessingException {
        return prepareParsed(organizationId, evaluationId, userId, options.questions(), options.options(),
                options.correct(), options.incorrect(), options.blank(), options.passingScore(),
                parser.parse(files, mapping), responseFilename);
    }

    private ImportSummary prepareParsed(long organizationId, long evaluationId, long userId,
            int questions, int options, BigDecimal correct, BigDecimal incorrect, BigDecimal blank,
            BigDecimal passingScore, ImportModels.Parsed parsed, String responseFilename)
            throws JsonProcessingException {
        if (questions < 1 || options < 2 || options > 26 || correct == null || correct.signum() < 0
                || incorrect == null || blank == null)
            throw ApiException.badRequest("Revisa las preguntas, opciones y valores de calificación");
        EvaluationModels.Evaluation evaluation = evaluations.require(organizationId, evaluationId);
        if (!List.of("BORRADOR", "CONFIGURADA", "EN_PROCESO").contains(evaluation.status()))
            throw ApiException.conflict("No se pueden importar hojas en el estado actual");
        Map<String, EvaluationModels.Form> forms = new HashMap<>();
        for (EvaluationModels.Form form : evaluations.forms(evaluationId)) forms.put(form.code().toUpperCase(), form);
        for (ImportModels.Key key : parsed.keys().values()) {
            String code = key.formCode().toUpperCase();
            EvaluationModels.Form existing = forms.get(code);
            int requiredQuestions = existing == null ? questions : existing.questions();
            if (key.answers().size() < requiredQuestions)
                throw ApiException.badRequest("La clave DBF tiene menos respuestas que las preguntas configuradas");
            if (existing == null) evaluations.createForm(evaluationId, code, questions, options);
        }
        if (evaluations.latestRule(evaluationId).isEmpty()) {
            long ruleId = evaluations.createRule(evaluationId, correct, incorrect, blank, passingScore, userId);
            audit.record(organizationId, evaluationId, userId, "REGLA_CREADA", "REGLA", ruleId, "{}");
        }
        for (ImportModels.Identification identity : parsed.identifications().values()) {
            String code = identity.participantCode();
            if (evaluations.registrationByCode(organizationId, evaluationId, code).isEmpty())
                evaluations.createAutomaticRegistration(organizationId, evaluationId, code);
        }
        if (!"EN_PROCESO".equals(evaluation.status()))
            evaluations.status(organizationId, evaluationId, "EN_PROCESO");
        return importParsed(organizationId, evaluationId, userId, parsed, responseFilename);
    }

    @Transactional
    public ImportSummary uploadMapped(long organizationId, long evaluationId, long userId,
            DbfFiles files, ColumnMapping mapping, String responseFilename) throws JsonProcessingException {
        return importParsed(organizationId, evaluationId, userId, parser.parse(files, mapping), responseFilename);
    }

    @Transactional
    public ImportSummary importFiles(long organizationId, long evaluationId, long userId,
            MultipartFile identificationFile, MultipartFile responseFile, MultipartFile keyFile) throws JsonProcessingException {
        return importParsed(organizationId, evaluationId, userId,
                parser.parse(identificationFile, responseFile, keyFile), responseFile.getOriginalFilename());
    }

    private ImportSummary importParsed(long organizationId, long evaluationId, long userId,
            ImportModels.Parsed parsed, String responseFilename) throws JsonProcessingException {
        EvaluationModels.Evaluation evaluation = evaluations.require(organizationId, evaluationId);
        if (!"EN_PROCESO".equals(evaluation.status()))
            throw ApiException.conflict("La evaluación debe estar en proceso para importar hojas");
        EvaluationModels.Rule rule = evaluations.latestRule(evaluationId)
                .orElseThrow(() -> ApiException.conflict("Configura una regla de calificación antes de importar"));
        policies.latest(evaluationId)
                .orElseThrow(() -> ApiException.conflict("Configura la política de resultados antes de importar"));
        Map<String, EvaluationModels.Form> forms = new HashMap<>();
        for (EvaluationModels.Form form : evaluations.forms(evaluationId)) forms.put(form.code().toUpperCase(), form);
        if (forms.isEmpty()) throw ApiException.conflict("Configura los temas antes de importar");
        Map<Long, SectionConfiguration> sectionsByForm = new HashMap<>();
        for (EvaluationModels.Form form : forms.values())
            sectionConfigurations.latest(form.id()).ifPresent(configuration -> sectionsByForm.put(form.id(), configuration));

        Map<String, Long> keyIds = new HashMap<>();
        Map<String, List<String>> keyAnswers = new HashMap<>();
        for (Map.Entry<String, ImportModels.Key> entry : parsed.keys().entrySet()) {
            EvaluationModels.Form form = forms.get(entry.getKey());
            if (form == null) continue;
            List<String> answers = takeAnswers(entry.getValue().answers(), form.questions());
            validateAnswers(answers, form.options(), false);
            long keyId = evaluations.createKey(form.id(), json.writeValueAsString(answers), userId);
            keyIds.put(entry.getKey(), keyId);
            audit.record(organizationId, evaluationId, userId, "CLAVE_IMPORTADA", "CLAVE", keyId,
                    "{\"temaId\":" + form.id() + "}");
            keyAnswers.put(entry.getKey(), answers);
        }

        long batchId = imports.createBatch(evaluationId, responseFilename, userId);
        int valid = 0;
        int errors = 0;
        Set<String> seen = new HashSet<>();
        for (ImportModels.Response response : parsed.responses()) {
            if (response.sheetCode().isBlank() || !seen.add(response.sheetCode())) { errors++; continue; }
            ImportModels.Identification identity = parsed.identifications().get(response.sheetCode());
            if (identity == null) { errors++; continue; }
            String formCode = identity.formCode().toUpperCase();
            EvaluationModels.Form form = forms.get(formCode);
            Long keyId = keyIds.get(formCode);
            if (form == null || keyId == null) { errors++; continue; }
            Long registrationId = evaluations.registrationByCode(organizationId, evaluationId, identity.participantCode()).orElse(null);
            if (registrationId == null || imports.hasSheet(evaluationId, response.sheetCode(), registrationId)) { errors++; continue; }

            List<String> answers = takeAnswers(response.answers(), form.questions());
            validateAnswers(answers, form.options(), true);
            String observation = null;
            if (response.formCode().isBlank()) observation = "No se indicó el tema en la hoja de respuestas";
            else if (!response.formCode().equalsIgnoreCase(identity.formCode()))
                observation = "El tema de respuestas no coincide con la identificación";
            String status = observation == null ? "VALIDADA" : "OBSERVADA";
            long sheetId = imports.createSheet(evaluationId, registrationId, form.id(), batchId,
                    response.sheetCode(), json.writeValueAsString(answers), status, observation);
            SectionConfiguration configuration = sectionsByForm.get(form.id());
            ImportModels.Score score = scoring.score(answers, keyAnswers.get(formCode), rule,
                    configuration == null ? List.of() : configuration.sections());
            imports.createResult(evaluationId, sheetId, form.id(), keyId, rule.id(),
                    configuration == null ? null : configuration.id(), json.writeValueAsString(score.sections()), score);
            if (observation != null) reviews.open(sheetId, observation, userId);
            if (observation == null) valid++; else errors++;
        }
        imports.finishBatch(batchId, parsed.responses().size(), valid, errors);
        exams.recalculateRanking(evaluationId);
        audit.record(organizationId, evaluationId, userId, "LOTE_IMPORTADO", "LOTE", batchId,
                "{\"validos\":" + valid + ",\"observadosOErrores\":" + errors + "}");
        return new ImportSummary(batchId, parsed.responses().size(), valid, errors);
    }

    private List<String> takeAnswers(List<String> source, int questions) {
        if (source.size() < questions) throw ApiException.badRequest("El DBF tiene menos respuestas que las preguntas del tema");
        return source.subList(0, questions);
    }

    private void validateAnswers(List<String> answers, int options, boolean blanksAllowed) {
        for (String answer : answers) {
            if (answer.isBlank()) continue;
            if (blanksAllowed && "*".equals(answer)) continue;
            if (answer.length() != 1 || answer.charAt(0) < 'A' || answer.charAt(0) >= 'A' + options)
                throw ApiException.badRequest("El DBF contiene una respuesta fuera de las opciones del tema");
        }
    }
}
