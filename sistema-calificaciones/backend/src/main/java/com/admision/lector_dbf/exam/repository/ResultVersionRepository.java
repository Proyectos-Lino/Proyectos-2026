package com.admision.lector_dbf.exam.repository;

import com.admision.lector_dbf.exam.model.ResultVersion;
import com.admision.lector_dbf.importer.model.ImportModels.Score;
import com.admision.lector_dbf.shared.PageResult;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public class ResultVersionRepository {
    private final JdbcClient db;
    public ResultVersionRepository(JdbcClient db) { this.db = db; }

    public record Source(long resultId, long sheetId, long formId, String answers) {}
    public record DetailSource(ResultVersion version, String responses, String key, String sections,
                               java.math.BigDecimal correctPoints, java.math.BigDecimal incorrectPoints,
                               java.math.BigDecimal blankPoints) {}

    public Optional<Source> source(long organizationId, long evaluationId, long sheetId) {
        return db.sql("SELECT r.id,h.id,h.formulario_id,h.respuestas::text FROM resultados r "
                + "JOIN hojas_respuesta h ON h.id=r.hoja_id JOIN evaluaciones e ON e.id=h.evaluacion_id "
                + "WHERE e.organizacion_id=:organization AND e.id=:evaluation AND h.id=:sheet FOR UPDATE OF r")
                .param("organization", organizationId).param("evaluation", evaluationId).param("sheet", sheetId)
                .query((rs, row) -> new Source(rs.getLong(1), rs.getLong(2), rs.getLong(3), rs.getString(4))).optional();
    }

    public List<Long> sheetIds(long evaluationId) {
        return db.sql("SELECT h.id FROM resultados r JOIN hojas_respuesta h ON h.id=r.hoja_id "
                + "WHERE h.evaluacion_id=:evaluation ORDER BY h.id")
                .param("evaluation", evaluationId).query(Long.class).list();
    }

    public long replace(Source source, long keyId, long ruleId, Long sectionId, String details,
                        Score score, String reason, long userId) {
        db.sql("UPDATE resultados SET clave_id=:key,regla_id=:rule,configuracion_seccion_id=:sections,"
                + "detalle_secciones=CAST(:details AS jsonb),correctas=:correct,incorrectas=:incorrect,"
                + "blancas=:blank,puntaje=:score,calculado_en=now() WHERE id=:result")
                .param("key", keyId).param("rule", ruleId).param("sections", sectionId)
                .param("details", details).param("correct", score.correct()).param("incorrect", score.incorrect())
                .param("blank", score.blank()).param("score", score.points()).param("result", source.resultId()).update();
        return db.sql("INSERT INTO versiones_resultado(resultado_id,version,clave_id,regla_id,configuracion_seccion_id,"
                + "detalle_secciones,correctas,incorrectas,blancas,puntaje,motivo,calculada_por) "
                + "SELECT :result,coalesce(max(version),0)+1,:key,:rule,:sections,CAST(:details AS jsonb),"
                + ":correct,:incorrect,:blank,:score,:reason,:user FROM versiones_resultado "
                + "WHERE resultado_id=:result RETURNING id")
                .param("result", source.resultId()).param("key", keyId).param("rule", ruleId)
                .param("sections", sectionId).param("details", details)
                .param("correct", score.correct()).param("incorrect", score.incorrect())
                .param("blank", score.blank()).param("score", score.points())
                .param("reason", reason).param("user", userId).query(Long.class).single();
    }

    public PageResult<ResultVersion> page(long organizationId, long evaluationId, long sheetId,
                                           int page, int size) {
        String source = " FROM versiones_resultado v JOIN resultados r ON r.id=v.resultado_id "
                + "JOIN hojas_respuesta h ON h.id=r.hoja_id JOIN evaluaciones e ON e.id=h.evaluacion_id "
                + "JOIN claves_respuesta k ON k.id=v.clave_id JOIN reglas_calificacion rc ON rc.id=v.regla_id "
                + "LEFT JOIN configuraciones_seccion cs ON cs.id=v.configuracion_seccion_id "
                + "WHERE e.organizacion_id=:organization AND e.id=:evaluation AND h.id=:sheet";
        long total = db.sql("SELECT count(*)" + source).param("organization", organizationId)
                .param("evaluation", evaluationId).param("sheet", sheetId).query(Long.class).single();
        List<ResultVersion> items = db.sql("SELECT v.id,v.version,k.version,rc.version,cs.version,v.correctas,"
                        + "v.incorrectas,v.blancas,v.puntaje,v.motivo,v.calculada_en" + source
                        + " ORDER BY v.version DESC LIMIT :size OFFSET :offset")
                .param("organization", organizationId).param("evaluation", evaluationId).param("sheet", sheetId)
                .param("size", size).param("offset", page * size)
                .query((rs, row) -> new ResultVersion(rs.getLong(1), rs.getInt(2), rs.getInt(3),
                        rs.getInt(4), (Integer) rs.getObject(5), rs.getInt(6), rs.getInt(7),
                        rs.getInt(8), rs.getBigDecimal(9), rs.getString(10),
                        rs.getObject(11, OffsetDateTime.class))).list();
        return PageResult.of(items, page, size, total);
    }

    public Optional<DetailSource> detail(long organizationId, long evaluationId, long sheetId, long versionId) {
        return db.sql("SELECT v.id,v.version,k.version,rc.version,cs.version,v.correctas,v.incorrectas,"
                + "v.blancas,v.puntaje,v.motivo,v.calculada_en,h.respuestas::text,k.respuestas::text,"
                + "v.detalle_secciones::text,rc.puntos_correcta,rc.puntos_incorrecta,rc.puntos_blanca "
                + "FROM versiones_resultado v JOIN resultados r ON r.id=v.resultado_id "
                + "JOIN hojas_respuesta h ON h.id=r.hoja_id JOIN evaluaciones e ON e.id=h.evaluacion_id "
                + "JOIN claves_respuesta k ON k.id=v.clave_id JOIN reglas_calificacion rc ON rc.id=v.regla_id "
                + "LEFT JOIN configuraciones_seccion cs ON cs.id=v.configuracion_seccion_id "
                + "WHERE e.organizacion_id=:organization AND e.id=:evaluation AND h.id=:sheet AND v.id=:version")
                .param("organization", organizationId).param("evaluation", evaluationId)
                .param("sheet", sheetId).param("version", versionId)
                .query((rs, row) -> new DetailSource(new ResultVersion(rs.getLong(1), rs.getInt(2),
                        rs.getInt(3), rs.getInt(4), (Integer) rs.getObject(5), rs.getInt(6),
                        rs.getInt(7), rs.getInt(8), rs.getBigDecimal(9), rs.getString(10),
                        rs.getObject(11, OffsetDateTime.class)), rs.getString(12), rs.getString(13),
                        rs.getString(14), rs.getBigDecimal(15), rs.getBigDecimal(16), rs.getBigDecimal(17))).optional();
    }
}
