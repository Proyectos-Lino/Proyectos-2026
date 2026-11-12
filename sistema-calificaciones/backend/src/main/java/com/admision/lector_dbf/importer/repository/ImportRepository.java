package com.admision.lector_dbf.importer.repository;

import com.admision.lector_dbf.importer.model.ImportModels;

import com.admision.lector_dbf.importer.model.ImportModels.*;
import com.admision.lector_dbf.shared.PageResult;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

@Repository
public class ImportRepository {
    private final JdbcClient db;
    public ImportRepository(JdbcClient db) { this.db = db; }

    public record ExistingData(Set<String> sheetCodes, Set<String> examinedParticipants,
                               Set<String> registeredParticipants) {}

    public void lockEvaluation(long evaluationId) {
        db.sql("SELECT id FROM evaluaciones WHERE id=:evaluation FOR UPDATE")
                .param("evaluation", evaluationId).query(Long.class).single();
    }

    public ExistingData existingData(long evaluationId) {
        Set<String> sheets = Set.copyOf(db.sql("SELECT codigo_hoja FROM hojas_respuesta "
                + "WHERE evaluacion_id=:evaluation AND codigo_hoja IS NOT NULL")
                .param("evaluation", evaluationId).query(String.class).list());
        Set<String> examined = Set.copyOf(db.sql("SELECT i.codigo_externo FROM hojas_respuesta h "
                + "JOIN inscripciones i ON i.id=h.inscripcion_id JOIN participantes p ON p.id=i.participante_id "
                + "WHERE h.evaluacion_id=:evaluation")
                .param("evaluation", evaluationId).query(String.class).list());
        Set<String> registered = Set.copyOf(db.sql("SELECT i.codigo_externo FROM inscripciones i "
                + "JOIN participantes p ON p.id=i.participante_id WHERE i.evaluacion_id=:evaluation")
                .param("evaluation", evaluationId).query(String.class).list());
        return new ExistingData(sheets, examined, registered);
    }

    public long createBatch(long evaluationId, String filename, long userId) {
        return db.sql("INSERT INTO lotes_importacion(evaluacion_id,nombre_archivo,estado,creado_por) VALUES (:evaluation,:filename,'PROCESANDO',:user) RETURNING id")
                .param("evaluation", evaluationId).param("filename", filename).param("user", userId).query(Long.class).single();
    }

    public void finishBatch(long batchId, int total, int valid, int errors) {
        db.sql("UPDATE lotes_importacion SET estado=:status,total_registros=:total,registros_validos=:valid,registros_error=:errors,terminado_en=now() WHERE id=:id")
                .param("status", errors == 0 ? "COMPLETADO" : "COMPLETADO_CON_ERRORES")
                .param("total", total).param("valid", valid).param("errors", errors).param("id", batchId).update();
    }

    public List<Batch> batches(long evaluationId) {
        return db.sql("SELECT id,nombre_archivo,estado,total_registros,registros_validos,registros_error FROM lotes_importacion WHERE evaluacion_id=:evaluation ORDER BY creado_en DESC")
                .param("evaluation", evaluationId).query((rs, row) -> new Batch(rs.getLong(1), rs.getString(2),
                        rs.getString(3), rs.getInt(4), rs.getInt(5), rs.getInt(6))).list();
    }

    public PageResult<Batch> batchesPage(long evaluationId, String query, String status, int page, int size) {
        String filter = " FROM lotes_importacion WHERE evaluacion_id=:evaluation AND (:status='' OR estado=:status)"
                + " AND lower(nombre_archivo) LIKE lower(:query)";
        String search = "%" + query.trim() + "%";
        long total = db.sql("SELECT count(*)" + filter).param("evaluation", evaluationId)
                .param("status", status).param("query", search).query(Long.class).single();
        List<Batch> items = db.sql("SELECT id,nombre_archivo,estado,total_registros,registros_validos,registros_error"
                        + filter + " ORDER BY creado_en DESC,id DESC LIMIT :size OFFSET :offset")
                .param("evaluation", evaluationId).param("status", status).param("query", search)
                .param("size", size).param("offset", page * size)
                .query((rs, row) -> new Batch(rs.getLong(1), rs.getString(2), rs.getString(3),
                        rs.getInt(4), rs.getInt(5), rs.getInt(6))).list();
        return PageResult.of(items, page, size, total);
    }

    public boolean hasSheet(long evaluationId, String sheetCode, long registrationId) {
        return db.sql("SELECT EXISTS(SELECT 1 FROM hojas_respuesta WHERE evaluacion_id=:evaluation AND (codigo_hoja=:code OR inscripcion_id=:registration))")
                .param("evaluation", evaluationId).param("code", sheetCode).param("registration", registrationId).query(Boolean.class).single();
    }

    public long createSheet(long evaluationId, long registrationId, long formId, long batchId,
                            String sheetCode, String answers, String status, String observation) {
        return db.sql("INSERT INTO hojas_respuesta(evaluacion_id,inscripcion_id,formulario_id,lote_id,codigo_hoja,respuestas,estado,observacion) VALUES (:evaluation,:registration,:form,:batch,:code,CAST(:answers AS jsonb),:status,:observation) RETURNING id")
                .param("evaluation", evaluationId).param("registration", registrationId).param("form", formId)
                .param("batch", batchId).param("code", sheetCode).param("answers", answers)
                .param("status", status).param("observation", observation).query(Long.class).single();
    }

    public void createResult(long evaluationId, long sheetId, long formId, long keyId, long ruleId,
                             Long sectionConfigurationId, String sectionDetails, ImportModels.Score score) {
        long resultId = db.sql("INSERT INTO resultados(evaluacion_id,hoja_id,formulario_id,clave_id,regla_id,configuracion_seccion_id,"
                + "detalle_secciones,correctas,incorrectas,blancas,puntaje,nota_oficial) "
                + "VALUES (:evaluation,:sheet,:form,:key,:rule,:sections,CAST(:details AS jsonb),:correct,:incorrect,:blank,:points,:official) RETURNING id")
                .param("evaluation", evaluationId).param("sheet", sheetId).param("form", formId)
                .param("key", keyId).param("rule", ruleId).param("sections", sectionConfigurationId)
                .param("details", sectionDetails).param("correct", score.correct())
                .param("incorrect", score.incorrect()).param("blank", score.blank())
                .param("points", score.points()).param("official", score.officialScore()).query(Long.class).single();
        db.sql("INSERT INTO versiones_resultado(resultado_id,version,clave_id,regla_id,configuracion_seccion_id,"
                + "detalle_secciones,correctas,incorrectas,blancas,puntaje,nota_oficial,motivo) VALUES "
                + "(:result,1,:key,:rule,:sections,CAST(:details AS jsonb),:correct,:incorrect,:blank,:points,:official,'Calificación inicial')")
                .param("result", resultId).param("key", keyId).param("rule", ruleId)
                .param("sections", sectionConfigurationId).param("details", sectionDetails)
                .param("correct", score.correct()).param("incorrect", score.incorrect())
                .param("blank", score.blank()).param("points", score.points()).param("official", score.officialScore()).update();
    }
}
