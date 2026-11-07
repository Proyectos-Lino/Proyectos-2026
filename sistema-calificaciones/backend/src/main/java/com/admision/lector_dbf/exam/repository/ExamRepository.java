package com.admision.lector_dbf.exam.repository;

import com.admision.lector_dbf.exam.model.ExamModels.*;
import com.admision.lector_dbf.evaluation.model.ScoringModels.RankingPolicy;
import com.admision.lector_dbf.evaluation.repository.RankingPolicyRepository;
import com.admision.lector_dbf.shared.ApiException;
import com.admision.lector_dbf.shared.PageResult;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public class ExamRepository {
    private static final String FROM = " FROM hojas_respuesta h JOIN evaluaciones e ON e.id=h.evaluacion_id "
            + "JOIN formularios f ON f.id=h.formulario_id JOIN inscripciones i ON i.id=h.inscripcion_id "
            + "JOIN participantes p ON p.id=i.participante_id LEFT JOIN grupos_evaluacion g ON g.id=i.grupo_id "
            + "LEFT JOIN resultados r ON r.hoja_id=h.id ";
    private static final String FILTER = " WHERE e.organizacion_id=:organization AND h.evaluacion_id=:evaluation "
            + "AND (:code='' OR lower(i.codigo_externo) LIKE lower(:code) OR lower(concat_ws(' ',p.nombres,p.apellidos)) LIKE lower(:code) "
            + "OR lower(coalesce(h.codigo_hoja,'')) LIKE lower(:code)) "
            + "AND (CAST(:groupId AS bigint) IS NULL OR i.grupo_id=:groupId) "
            + "AND (:status='' OR h.estado=:status) ";

    private final JdbcClient db;
    private final RankingPolicyRepository policies;
    public ExamRepository(JdbcClient db, RankingPolicyRepository policies) {
        this.db = db; this.policies = policies;
    }

    private Exam map(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new Exam(rs.getLong("id"), rs.getLong("evaluacion_id"), rs.getString("codigo_hoja"),
                rs.getString("codigo_participante"), rs.getString("nombre_participante"), rs.getString("nombre_grupo"),
                rs.getString("codigo_formulario"), rs.getString("estado"), rs.getString("observacion"),
                (Integer) rs.getObject("correctas"), (Integer) rs.getObject("incorrectas"),
                (Integer) rs.getObject("blancas"), rs.getBigDecimal("puntaje"),
                (Integer) rs.getObject("posicion"), (Boolean) rs.getObject("aprobado"),
                rs.getObject("creada_en", OffsetDateTime.class),(Long)rs.getObject("grupo_id"));
    }

    private String select() {
        return "SELECT h.id,h.evaluacion_id,h.codigo_hoja,i.codigo_externo AS codigo_participante,"
                + "concat_ws(' ',p.nombres,p.apellidos) AS nombre_participante,g.nombre AS nombre_grupo,"
                + "f.codigo AS codigo_formulario,h.estado,h.observacion,r.correctas,r.incorrectas,r.blancas,"
                + "r.puntaje,r.posicion,r.aprobado,h.creada_en,i.grupo_id";
    }

    public Page<Exam> page(long organizationId, long evaluationId, String code, Long groupId,
                           String status, int page, int size, String order) {
        String search = code.isBlank() ? "" : "%" + code.trim() + "%";
        String sort = switch (order) {
            case "puntaje-asc" -> " ORDER BY r.puntaje ASC NULLS LAST,h.id";
            case "fecha-desc" -> " ORDER BY h.creada_en DESC,h.id DESC";
            default -> " ORDER BY r.puntaje DESC NULLS LAST,h.id";
        };
        long total = db.sql("SELECT count(*)" + FROM + FILTER).param("organization", organizationId)
                .param("evaluation", evaluationId).param("code", search).param("groupId", groupId)
                .param("status", status).query(Long.class).single();
        List<Exam> items = db.sql(select() + FROM + FILTER + sort + " LIMIT :size OFFSET :offset")
                .param("organization", organizationId).param("evaluation", evaluationId).param("code", search)
                .param("groupId", groupId).param("status", status).param("size", size)
                .param("offset", page * size).query((rs, row) -> map(rs)).list();
        return new Page<>(items, page, size, total, (int) ((total + size - 1) / size));
    }

    public Optional<Exam> find(long organizationId, long evaluationId, long sheetId) {
        return db.sql(select() + FROM + " WHERE e.organizacion_id=:organization AND h.evaluacion_id=:evaluation AND h.id=:sheet")
                .param("organization", organizationId).param("evaluation", evaluationId).param("sheet", sheetId)
                .query((rs, row) -> map(rs)).optional();
    }

    public List<Exam> allForPdf(long organizationId, long evaluationId) {
        return db.sql(select() + FROM + " WHERE e.organizacion_id=:organization AND h.evaluacion_id=:evaluation ORDER BY g.nombre NULLS FIRST,r.posicion NULLS LAST,r.puntaje DESC,h.id")
                .param("organization", organizationId).param("evaluation", evaluationId)
                .query((rs, row) -> map(rs)).list();
    }

    public void recalculateRanking(long evaluationId) {
        RankingPolicy policy = policies.latest(evaluationId)
                .orElseThrow(() -> ApiException.conflict("Configura la política de resultados"));
        String partition = "GRUPO".equals(policy.scope()) ? "i.grupo_id" : "1";
        String capacity = "GRUPO".equals(policy.scope()) ? "g.cupo" : ":capacity";
        String order = switch (policy.tieBreak()) {
            case "CORRECTAS" -> "r.puntaje DESC,r.correctas DESC,r.incorrectas ASC,i.codigo_externo,h.id";
            case "MENOS_INCORRECTAS" -> "r.puntaje DESC,r.incorrectas ASC,r.correctas DESC,i.codigo_externo,h.id";
            case "CODIGO" -> "r.puntaje DESC,i.codigo_externo,h.id";
            default -> "r.puntaje DESC";
        };
        String function = "COMPARTIR_PUESTO".equals(policy.tieBreak()) ? "rank()" : "row_number()";
        String passed = switch (policy.approvalMode()) {
            case "NINGUNA" -> "NULL";
            case "MINIMA" -> "CASE WHEN ranked.minimum IS NULL THEN NULL ELSE ranked.puntaje>=ranked.minimum END";
            case "VACANTES" -> "CASE WHEN ranked.capacity IS NULL THEN NULL ELSE ranked.place<=ranked.capacity END";
            case "AMBAS" -> "CASE WHEN ranked.capacity IS NULL OR ranked.minimum IS NULL THEN NULL "
                    + "ELSE ranked.place<=ranked.capacity AND ranked.puntaje>=ranked.minimum END";
            default -> "CASE WHEN ranked.capacity IS NOT NULL THEN ranked.place<=ranked.capacity "
                    + "WHEN ranked.minimum IS NOT NULL THEN ranked.puntaje>=ranked.minimum ELSE NULL END";
        };
        db.sql("UPDATE resultados r SET posicion=NULL, aprobado=NULL FROM hojas_respuesta h WHERE r.hoja_id=h.id AND h.evaluacion_id=:evaluation")
                .param("evaluation", evaluationId).update();
        db.sql("""
                WITH ranked AS (
                    SELECT r.id, %s OVER (PARTITION BY %s ORDER BY %s) AS place,
                           %s AS capacity, rc.minima_aprobacion AS minimum, r.puntaje
                    FROM resultados r JOIN hojas_respuesta h ON h.id=r.hoja_id
                    JOIN inscripciones i ON i.id=h.inscripcion_id JOIN participantes p ON p.id=i.participante_id
                    LEFT JOIN grupos_evaluacion g ON g.id=i.grupo_id
                    JOIN reglas_calificacion rc ON rc.id=r.regla_id
                    WHERE h.evaluacion_id=:evaluation AND h.estado='VALIDADA'
                )
                UPDATE resultados r SET posicion=ranked.place,aprobado=%s FROM ranked WHERE r.id=ranked.id
                """.formatted(function, partition, order, capacity, passed))
                .param("evaluation", evaluationId).param("capacity", policy.globalCapacity()).update();
        recordDecisions(evaluationId, policy, capacity);
    }

    public Optional<ExplanationData> explanation(long organizationId, long evaluationId, long sheetId) {
        return db.sql("""
                SELECT h.id,f.codigo,f.numero_preguntas,k.version AS clave_version,rc.version AS regla_version,
                       rc.puntos_correcta,rc.puntos_incorrecta,rc.puntos_blanca,rc.minima_aprobacion,
                       cs.version AS seccion_version,r.puntaje,r.detalle_secciones::text AS detalle
                FROM hojas_respuesta h JOIN evaluaciones e ON e.id=h.evaluacion_id
                JOIN resultados r ON r.hoja_id=h.id JOIN formularios f ON f.id=r.formulario_id
                JOIN claves_respuesta k ON k.id=r.clave_id JOIN reglas_calificacion rc ON rc.id=r.regla_id
                LEFT JOIN configuraciones_seccion cs ON cs.id=r.configuracion_seccion_id
                WHERE e.organizacion_id=:organization AND h.evaluacion_id=:evaluation AND h.id=:sheet
                """).param("organization", organizationId).param("evaluation", evaluationId).param("sheet", sheetId)
                .query((rs, row) -> new ExplanationData(rs.getLong(1), rs.getString(2), rs.getInt(3),
                        rs.getInt(4), rs.getInt(5), rs.getBigDecimal(6), rs.getBigDecimal(7),
                        rs.getBigDecimal(8), rs.getBigDecimal(9), (Integer) rs.getObject(10),
                        rs.getBigDecimal(11), rs.getString(12))).optional();
    }

    public PageResult<RankingDecision> decisions(long organizationId, long evaluationId,
                                                  long sheetId, int page, int size) {
        String source = " FROM decisiones_resultado d JOIN resultados r ON r.id=d.resultado_id "
                + "JOIN hojas_respuesta h ON h.id=r.hoja_id JOIN evaluaciones e ON e.id=h.evaluacion_id "
                + "JOIN politicas_resultado p ON p.id=d.politica_id "
                + "WHERE e.organizacion_id=:organization AND h.evaluacion_id=:evaluation AND h.id=:sheet";
        long total = db.sql("SELECT count(*)" + source).param("organization", organizationId)
                .param("evaluation", evaluationId).param("sheet", sheetId).query(Long.class).single();
        List<RankingDecision> items = db.sql("SELECT d.id,p.version,p.alcance_ranking,p.modo_aprobacion,p.desempate,"
                        + "d.cupo_aplicado,d.minima_aplicada,d.posicion,d.aprobado,d.registrada_en"
                        + source + " ORDER BY d.id DESC LIMIT :size OFFSET :offset")
                .param("organization", organizationId).param("evaluation", evaluationId)
                .param("sheet", sheetId).param("size", size).param("offset", page * size)
                .query((rs, row) -> new RankingDecision(rs.getLong(1), rs.getInt(2), rs.getString(3),
                        rs.getString(4), rs.getString(5), (Integer) rs.getObject(6), rs.getBigDecimal(7),
                        (Integer) rs.getObject(8), (Boolean) rs.getObject(9),
                        rs.getObject(10, OffsetDateTime.class))).list();
        return PageResult.of(items, page, size, total);
    }

    private void recordDecisions(long evaluationId, RankingPolicy policy, String capacity) {
        String appliedCapacity = switch (policy.approvalMode()) {
            case "VACANTES", "AMBAS", "VACANTES_O_MINIMA" -> capacity;
            default -> "NULL";
        };
        String appliedMinimum = switch (policy.approvalMode()) {
            case "MINIMA", "AMBAS" -> "rc.minima_aprobacion";
            case "VACANTES_O_MINIMA" -> "CASE WHEN " + capacity + " IS NULL THEN rc.minima_aprobacion ELSE NULL END";
            default -> "NULL";
        };
        db.sql("""
                INSERT INTO decisiones_resultado(evaluacion_id,resultado_id,politica_id,posicion,aprobado,cupo_aplicado,minima_aplicada)
                SELECT :evaluation,r.id,:policy,r.posicion,r.aprobado,%s,%s
                FROM resultados r JOIN hojas_respuesta h ON h.id=r.hoja_id
                JOIN inscripciones i ON i.id=h.inscripcion_id
                LEFT JOIN grupos_evaluacion g ON g.id=i.grupo_id
                JOIN reglas_calificacion rc ON rc.id=r.regla_id
                LEFT JOIN LATERAL (
                    SELECT d.id,d.politica_id,d.posicion,d.aprobado,d.cupo_aplicado,d.minima_aplicada
                    FROM decisiones_resultado d WHERE d.resultado_id=r.id ORDER BY d.id DESC LIMIT 1
                ) previous ON true
                WHERE h.evaluacion_id=:evaluation AND
                  (previous.id IS NULL OR
                   (previous.politica_id,previous.posicion,previous.aprobado,previous.cupo_aplicado,previous.minima_aplicada)
                   IS DISTINCT FROM (:policy,r.posicion,r.aprobado,%s,%s))
                """.formatted(appliedCapacity, appliedMinimum, appliedCapacity, appliedMinimum))
                .param("evaluation", evaluationId).param("policy", policy.id())
                .param("capacity", policy.globalCapacity()).update();
    }
}
