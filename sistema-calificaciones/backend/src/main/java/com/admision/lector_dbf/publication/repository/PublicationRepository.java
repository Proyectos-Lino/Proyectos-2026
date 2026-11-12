package com.admision.lector_dbf.publication.repository;

import com.admision.lector_dbf.publication.model.PublicationModels.*;
import com.admision.lector_dbf.shared.ApiException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public class PublicationRepository {
    private final JdbcClient db;
    public PublicationRepository(JdbcClient db) { this.db = db; }

    public void lock(long organizationId, long evaluationId) {
        db.sql("SELECT id FROM evaluaciones WHERE organizacion_id=:organization AND id=:evaluation FOR UPDATE")
                .param("organization", organizationId).param("evaluation", evaluationId)
                .query(Long.class).optional().orElseThrow(() -> ApiException.notFound("Evaluación"));
    }

    public void assertMutable(long organizationId, long evaluationId) {
        lock(organizationId, evaluationId);
        boolean frozen = db.sql("SELECT e.estado IN ('PUBLICADA','ARCHIVADA') OR "
                + "(cp.aprobada_en IS NOT NULL) FROM evaluaciones e "
                + "LEFT JOIN configuraciones_publicacion cp ON cp.evaluacion_id=e.id "
                + "WHERE e.organizacion_id=:organization AND e.id=:evaluation")
                .param("organization", organizationId).param("evaluation", evaluationId)
                .query(Boolean.class).single();
        if (frozen) throw ApiException.conflict("Los resultados aprobados o publicados no admiten cambios");
    }

    public Policy policy(long evaluationId) {
        return db.sql("SELECT mostrar_puntaje,mostrar_puesto,mostrar_aprobacion,mostrar_conteos,"
                + "visible_desde,visible_hasta,aprobada_por,aprobada_en,publicada_por,publicada_en "
                + "FROM configuraciones_publicacion WHERE evaluacion_id=:evaluation")
                .param("evaluation", evaluationId).query((rs, row) -> new Policy(rs.getBoolean(1),
                        rs.getBoolean(2), rs.getBoolean(3), rs.getBoolean(4),
                        rs.getObject(5, OffsetDateTime.class), rs.getObject(6, OffsetDateTime.class),
                        (Long) rs.getObject(7), rs.getObject(8, OffsetDateTime.class),
                        (Long) rs.getObject(9), rs.getObject(10, OffsetDateTime.class), true))
                .optional().orElse(new Policy(true, false, true, false, null, null, null, null, null, null, false));
    }

    public void save(long evaluationId, boolean score, boolean position, boolean passed, boolean counts,
                     OffsetDateTime from, OffsetDateTime until) {
        db.sql("INSERT INTO configuraciones_publicacion(evaluacion_id,mostrar_puntaje,mostrar_puesto,"
                + "mostrar_aprobacion,mostrar_conteos,visible_desde,visible_hasta) "
                + "VALUES (:evaluation,:score,:position,:passed,:counts,:from,:until) "
                + "ON CONFLICT (evaluacion_id) DO UPDATE SET mostrar_puntaje=:score,mostrar_puesto=:position,"
                + "mostrar_aprobacion=:passed,mostrar_conteos=:counts,visible_desde=:from,visible_hasta=:until,"
                + "aprobada_por=CASE WHEN (SELECT estado FROM evaluaciones WHERE id=:evaluation)='CERRADA' "
                + "THEN NULL ELSE configuraciones_publicacion.aprobada_por END,"
                + "aprobada_en=CASE WHEN (SELECT estado FROM evaluaciones WHERE id=:evaluation)='CERRADA' "
                + "THEN NULL ELSE configuraciones_publicacion.aprobada_en END")
                .param("evaluation", evaluationId).param("score", score).param("position", position)
                .param("passed", passed).param("counts", counts).param("from", from).param("until", until).update();
    }

    public void approve(long evaluationId, long userId) {
        db.sql("INSERT INTO configuraciones_publicacion(evaluacion_id,aprobada_por,aprobada_en) "
                + "VALUES (:evaluation,:user,now()) ON CONFLICT (evaluacion_id) DO UPDATE SET "
                + "aprobada_por=:user,aprobada_en=now()")
                .param("evaluation", evaluationId).param("user", userId).update();
    }

    public long ungroupedResults(long evaluation) {
        return db.sql("SELECT count(*) FROM hojas_respuesta h JOIN inscripciones i ON i.id=h.inscripcion_id "
                + "WHERE h.evaluacion_id=:evaluation AND h.estado='VALIDADA' AND i.grupo_id IS NULL "
                + "AND EXISTS(SELECT 1 FROM grupos_evaluacion WHERE evaluacion_id=:evaluation) "
                + "AND (SELECT alcance_ranking FROM politicas_resultado WHERE evaluacion_id=:evaluation ORDER BY version DESC LIMIT 1)='GRUPO'")
                .param("evaluation", evaluation).query(Long.class).single();
    }

    public void publish(long evaluationId, long userId) {
        db.sql("UPDATE configuraciones_publicacion SET publicada_por=:user,publicada_en=now() "
                + "WHERE evaluacion_id=:evaluation AND aprobada_en IS NOT NULL")
                .param("user", userId).param("evaluation", evaluationId).update();
        db.sql("UPDATE evaluaciones SET estado='PUBLICADA',actualizada_en=now() WHERE id=:evaluation AND estado='CERRADA'")
                .param("evaluation", evaluationId).update();
    }

    public boolean hasBlockingSheets(long evaluationId) {
        return db.sql("SELECT EXISTS(SELECT 1 FROM hojas_respuesta WHERE evaluacion_id=:evaluation "
                + "AND estado IN ('RECIBIDA','OBSERVADA'))")
                .param("evaluation", evaluationId).query(Boolean.class).single();
    }

    public boolean hasUnfinishedBatches(long evaluationId) {
        return db.sql("SELECT EXISTS(SELECT 1 FROM lotes_importacion WHERE evaluacion_id=:evaluation "
                + "AND estado IN ('PENDIENTE','PROCESANDO'))")
                .param("evaluation", evaluationId).query(Boolean.class).single();
    }

    public long validResults(long evaluationId) {
        return db.sql("SELECT count(*) FROM resultados r JOIN hojas_respuesta h ON h.id=r.hoja_id "
                + "WHERE h.evaluacion_id=:evaluation AND h.estado='VALIDADA'")
                .param("evaluation", evaluationId).query(Long.class).single();
    }

    public boolean hasStaleResults(long evaluationId) {
        return db.sql("SELECT EXISTS(SELECT 1 FROM resultados r JOIN hojas_respuesta h ON h.id=r.hoja_id "
                + "JOIN LATERAL (SELECT id FROM claves_respuesta WHERE formulario_id=h.formulario_id "
                + "ORDER BY version DESC LIMIT 1) k ON true "
                + "JOIN LATERAL (SELECT id FROM reglas_calificacion WHERE evaluacion_id=h.evaluacion_id "
                + "ORDER BY version DESC LIMIT 1) rc ON true "
                + "LEFT JOIN LATERAL (SELECT id FROM configuraciones_seccion WHERE formulario_id=h.formulario_id "
                + "ORDER BY version DESC LIMIT 1) cs ON true "
                + "WHERE h.evaluacion_id=:evaluation AND (r.clave_id<>k.id OR r.regla_id<>rc.id "
                + "OR r.configuracion_seccion_id IS DISTINCT FROM cs.id))")
                .param("evaluation", evaluationId).query(Boolean.class).single();
    }

    public List<AccessCandidate> candidates(long evaluationId) {
        return db.sql("SELECT i.id,i.codigo_externo,concat_ws(' ',p.nombres,p.apellidos),p.correo FROM inscripciones i JOIN participantes p ON p.id=i.participante_id "
                + "JOIN hojas_respuesta h ON h.inscripcion_id=i.id JOIN resultados r ON r.hoja_id=h.id "
                + "WHERE i.evaluacion_id=:evaluation AND h.estado='VALIDADA' ORDER BY i.codigo_externo")
                .param("evaluation", evaluationId)
                .query((rs, row) -> new AccessCandidate(rs.getLong(1), rs.getString(2),rs.getString(3),rs.getString(4))).list();
    }

    public void saveAccess(long registrationId, String hash, long userId) {
        db.sql("INSERT INTO accesos_resultado(inscripcion_id,token_hash,generado_por) "
                + "VALUES (:registration,:hash,:user) ON CONFLICT (inscripcion_id) DO UPDATE SET "
                + "token_hash=:hash,generado_por=:user,generado_en=now(),revocado_en=NULL")
                .param("registration", registrationId).param("hash", hash).param("user", userId).update();
    }

    public Optional<PublicResult> lookup(String hash) {
        return db.sql("SELECT e.nombre,i.codigo_externo,concat_ws(' ',p.nombres,p.apellidos),f.codigo,"
                + "CASE WHEN cp.mostrar_puntaje THEN coalesce(r.nota_oficial,r.puntaje) END,"
                + "CASE WHEN cp.mostrar_puesto THEN r.posicion END,"
                + "CASE WHEN cp.mostrar_aprobacion THEN r.aprobado END,"
                + "CASE WHEN cp.mostrar_conteos THEN r.correctas END,"
                + "CASE WHEN cp.mostrar_conteos THEN r.incorrectas END,"
                + "CASE WHEN cp.mostrar_conteos THEN r.blancas END,e.tipo "
                + "FROM accesos_resultado a JOIN inscripciones i ON i.id=a.inscripcion_id "
                + "JOIN participantes p ON p.id=i.participante_id JOIN evaluaciones e ON e.id=i.evaluacion_id "
                + "JOIN configuraciones_publicacion cp ON cp.evaluacion_id=e.id "
                + "JOIN hojas_respuesta h ON h.inscripcion_id=i.id JOIN resultados r ON r.hoja_id=h.id "
                + "JOIN formularios f ON f.id=h.formulario_id "
                + "WHERE a.token_hash=:hash AND a.revocado_en IS NULL AND e.estado='PUBLICADA' "
                + "AND cp.publicada_en IS NOT NULL AND h.estado='VALIDADA' "
                + "AND (cp.visible_desde IS NULL OR cp.visible_desde<=now()) "
                + "AND (cp.visible_hasta IS NULL OR cp.visible_hasta>now())")
                .param("hash", hash).query((rs, row) -> new PublicResult(rs.getString(1), rs.getString(2),
                        rs.getString(3), rs.getString(4), rs.getBigDecimal(5), (Integer) rs.getObject(6),
                        (Boolean) rs.getObject(7), (Integer) rs.getObject(8),
                        (Integer) rs.getObject(9), (Integer) rs.getObject(10),rs.getString(11))).optional();
    }

    public ReportContext reportContext(String hash,PublicResult result) {
        return db.sql("SELECT e.organizacion_id,e.id,e.codigo,g.nombre,e.fecha_evaluacion,e.tipo FROM accesos_resultado a "
                + "JOIN inscripciones i ON i.id=a.inscripcion_id JOIN evaluaciones e ON e.id=i.evaluacion_id "
                + "LEFT JOIN grupos_evaluacion g ON g.id=i.grupo_id WHERE a.token_hash=:hash AND a.revocado_en IS NULL")
                .param("hash",hash).query((rs,row) -> new ReportContext(rs.getLong(1),rs.getLong(2),rs.getString(3),
                        rs.getString(4),rs.getDate(5)==null ? null : rs.getDate(5).toLocalDate(),rs.getString(6),result)).single();
    }
}
