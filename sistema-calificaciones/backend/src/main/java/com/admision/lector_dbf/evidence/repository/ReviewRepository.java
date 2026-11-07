package com.admision.lector_dbf.evidence.repository;

import com.admision.lector_dbf.evidence.model.ReviewModels.*;
import com.admision.lector_dbf.shared.PageResult;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public class ReviewRepository {
    private final JdbcClient db;
    public ReviewRepository(JdbcClient db) { this.db = db; }

    public Optional<Long> sheet(long organizationId, long evaluationId, long sheetId) {
        return db.sql("SELECT h.id FROM hojas_respuesta h JOIN evaluaciones e ON e.id=h.evaluacion_id WHERE e.organizacion_id=:organization AND e.id=:evaluation AND h.id=:sheet")
                .param("organization", organizationId).param("evaluation", evaluationId).param("sheet", sheetId).query(Long.class).optional();
    }

    public long open(long sheetId, String reason, long userId) {
        return db.sql("INSERT INTO revisiones(hoja_id,motivo,abierta_por) VALUES (:sheet,:reason,:user) RETURNING id")
                .param("sheet", sheetId).param("reason", reason).param("user", userId).query(Long.class).single();
    }

    public void markObserved(long sheetId, String reason) {
        db.sql("UPDATE hojas_respuesta SET estado='OBSERVADA',observacion=:reason WHERE id=:sheet")
                .param("sheet", sheetId).param("reason", reason).update();
    }

    public List<Review> reviews(long organizationId, long evaluationId, long sheetId) {
        return db.sql("SELECT r.id,r.hoja_id,r.estado,r.motivo,r.resolucion,r.abierta_en,r.asignada_a,u.nombre,op.nombre,rp.nombre,r.resuelta_en FROM revisiones r LEFT JOIN usuarios u ON u.id=r.asignada_a LEFT JOIN usuarios op ON op.id=r.abierta_por LEFT JOIN usuarios rp ON rp.id=r.resuelta_por JOIN hojas_respuesta h ON h.id=r.hoja_id JOIN evaluaciones e ON e.id=h.evaluacion_id WHERE e.organizacion_id=:organization AND e.id=:evaluation AND h.id=:sheet ORDER BY r.abierta_en DESC,r.id DESC")
                .param("organization", organizationId).param("evaluation", evaluationId).param("sheet", sheetId)
                .query((rs, row) -> new Review(rs.getLong(1), rs.getLong(2), rs.getString(3), rs.getString(4),
                        rs.getString(5), rs.getObject(6, OffsetDateTime.class), (Long) rs.getObject(7), rs.getString(8),rs.getString(9),rs.getString(10),rs.getObject(11,OffsetDateTime.class))).list();
    }

    public Optional<Review> review(long organizationId, long evaluationId, long reviewId) {
        return db.sql("SELECT r.id,r.hoja_id,r.estado,r.motivo,r.resolucion,r.abierta_en,r.asignada_a,u.nombre,op.nombre,rp.nombre,r.resuelta_en FROM revisiones r LEFT JOIN usuarios u ON u.id=r.asignada_a LEFT JOIN usuarios op ON op.id=r.abierta_por LEFT JOIN usuarios rp ON rp.id=r.resuelta_por JOIN hojas_respuesta h ON h.id=r.hoja_id JOIN evaluaciones e ON e.id=h.evaluacion_id WHERE e.organizacion_id=:organization AND e.id=:evaluation AND r.id=:review")
                .param("organization", organizationId).param("evaluation", evaluationId).param("review", reviewId)
                .query((rs, row) -> new Review(rs.getLong(1), rs.getLong(2), rs.getString(3), rs.getString(4),
                        rs.getString(5), rs.getObject(6, OffsetDateTime.class), (Long) rs.getObject(7), rs.getString(8),rs.getString(9),rs.getString(10),rs.getObject(11,OffsetDateTime.class))).optional();
    }

    public void resolve(long reviewId, String decision, String resolution, long userId) {
        db.sql("UPDATE revisiones SET estado=:decision,resolucion=:resolution,resuelta_por=:user,asignada_a=coalesce(asignada_a,:user),resuelta_en=now() WHERE id=:review AND estado IN ('ABIERTA','EN_REVISION')")
                .param("decision", decision).param("resolution", resolution).param("user", userId)
                .param("review", reviewId).update();
    }

    public void setSheetStatus(long sheetId, String status, String observation) {
        db.sql("UPDATE hojas_respuesta SET estado=:status,observacion=:observation WHERE id=:sheet")
                .param("status", status).param("observation", observation).param("sheet", sheetId).update();
    }

    public boolean hasOpen(long sheetId) {
        return db.sql("SELECT EXISTS(SELECT 1 FROM revisiones WHERE hoja_id=:sheet AND estado IN ('ABIERTA','EN_REVISION'))")
                .param("sheet", sheetId).query(Boolean.class).single();
    }

    public long pending(long evaluationId) {
        return db.sql("SELECT count(*) FROM revisiones r JOIN hojas_respuesta h ON h.id=r.hoja_id "
                + "WHERE h.evaluacion_id=:evaluation AND r.estado IN ('ABIERTA','EN_REVISION')")
                .param("evaluation", evaluationId).query(Long.class).single();
    }

    public PageResult<ReviewCase> cases(long organizationId, long evaluationId, String query,
                                        String status, int page, int size) {
        return cases(organizationId,evaluationId,query,status,page,size,"ALL",0);
    }
    public PageResult<ReviewCase> cases(long organizationId,long evaluationId,String query,String status,int page,int size,String assignment,long actor) {
        String source = " FROM revisiones r JOIN hojas_respuesta h ON h.id=r.hoja_id "
                + "JOIN evaluaciones e ON e.id=h.evaluacion_id JOIN inscripciones i ON i.id=h.inscripcion_id "
                + "JOIN participantes p ON p.id=i.participante_id LEFT JOIN usuarios u ON u.id=r.asignada_a "
                + "WHERE e.organizacion_id=:organization AND h.evaluacion_id=:evaluation "
                + "AND (:assignment='ALL' OR (:assignment='MINE' AND r.asignada_a=:actor) OR (:assignment='UNASSIGNED' AND r.asignada_a IS NULL)) "
                + "AND (:status='' OR r.estado=:status OR (:status='PENDIENTES' "
                + "AND r.estado IN ('ABIERTA','EN_REVISION'))) AND (lower(i.codigo_externo) LIKE lower(:query) "
                + "OR lower(concat_ws(' ',p.nombres,p.apellidos)) LIKE lower(:query) "
                + "OR lower(coalesce(h.codigo_hoja,'')) LIKE lower(:query) OR lower(r.motivo) LIKE lower(:query))";
        String search = "%" + query.trim() + "%";
        long total = db.sql("SELECT count(*)" + source).param("organization", organizationId)
                .param("evaluation", evaluationId).param("status", status).param("query", search).param("assignment",assignment).param("actor",actor)
                .query(Long.class).single();
        List<ReviewCase> items = db.sql("SELECT r.id,h.id,i.codigo_externo,concat_ws(' ',p.nombres,p.apellidos),"
                        + "h.codigo_hoja,r.estado,r.motivo,r.asignada_a,u.nombre,r.abierta_en" + source
                        + " ORDER BY CASE WHEN r.estado IN ('ABIERTA','EN_REVISION') THEN 0 ELSE 1 END,"
                        + "r.abierta_en DESC,r.id DESC LIMIT :size OFFSET :offset")
                .param("organization", organizationId).param("evaluation", evaluationId)
                .param("status", status).param("query", search).param("assignment",assignment).param("actor",actor).param("size", size).param("offset", page * size)
                .query((rs, row) -> new ReviewCase(rs.getLong(1), rs.getLong(2), rs.getString(3),
                        rs.getString(4), rs.getString(5), rs.getString(6), rs.getString(7),
                        (Long) rs.getObject(8), rs.getString(9), rs.getObject(10, OffsetDateTime.class))).list();
        return PageResult.of(items, page, size, total);
    }

    public Neighbors neighbors(long evaluation,long sheet,String assignment,long actor) {
        return db.sql("""
                WITH anchor AS (SELECT coalesce(max(id),0) AS id FROM revisiones WHERE hoja_id=:sheet),
                pending AS (SELECT r.id,r.hoja_id FROM revisiones r JOIN hojas_respuesta h ON h.id=r.hoja_id
                    WHERE h.evaluacion_id=:evaluation AND r.estado IN ('ABIERTA','EN_REVISION') AND h.id<>:sheet
                    AND (:assignment='ALL' OR (:assignment='MINE' AND r.asignada_a=:actor)
                        OR (:assignment='UNASSIGNED' AND r.asignada_a IS NULL)))
                SELECT (SELECT hoja_id FROM pending,anchor WHERE pending.id>anchor.id ORDER BY pending.id LIMIT 1),
                       (SELECT hoja_id FROM pending,anchor WHERE pending.id<anchor.id ORDER BY pending.id DESC LIMIT 1)
                """).param("evaluation",evaluation).param("sheet",sheet).param("assignment",assignment).param("actor",actor)
                .query((rs,n)->new Neighbors((Long)rs.getObject(1),(Long)rs.getObject(2))).single();
    }

    public int assign(long reviewId, Long userId) {
        return db.sql("UPDATE revisiones SET asignada_a=:user,estado=:status WHERE id=:review "
                        + "AND estado IN ('ABIERTA','EN_REVISION')")
                .param("user", userId).param("status", userId == null ? "ABIERTA" : "EN_REVISION")
                .param("review", reviewId).update();
    }

    public boolean eligibleAssignee(long organizationId, long userId) {
        return db.sql("SELECT EXISTS(SELECT 1 FROM membresias m JOIN usuarios u ON u.id=m.usuario_id "
                + "WHERE m.organizacion_id=:organization AND m.usuario_id=:user AND m.activa AND u.activo "
                + "AND m.rol IN ('ADMINISTRADOR','REVISOR'))")
                .param("organization", organizationId).param("user", userId).query(Boolean.class).single();
    }

    public PageResult<Assignee> assignees(long organizationId, String query, int page, int size) {
        String source = " FROM membresias m JOIN usuarios u ON u.id=m.usuario_id "
                + "WHERE m.organizacion_id=:organization AND m.activa AND u.activo "
                + "AND m.rol IN ('ADMINISTRADOR','REVISOR') AND lower(u.nombre) LIKE lower(:query)";
        String search = "%" + query.trim() + "%";
        long total = db.sql("SELECT count(*)" + source).param("organization", organizationId)
                .param("query", search).query(Long.class).single();
        List<Assignee> items = db.sql("SELECT u.id,u.nombre,m.rol" + source
                        + " ORDER BY u.nombre,u.id LIMIT :size OFFSET :offset")
                .param("organization", organizationId).param("query", search)
                .param("size", size).param("offset", page * size)
                .query((rs, row) -> new Assignee(rs.getLong(1), rs.getString(2), rs.getString(3))).list();
        return PageResult.of(items, page, size, total);
    }

    public int updateSheetReason(long organizationId, long evaluationId, long sheetId, String reason) {
        return db.sql("""
                UPDATE hojas_respuesta h SET observacion=:reason
                FROM evaluaciones e WHERE e.id=h.evaluacion_id AND e.organizacion_id=:organization
                AND h.evaluacion_id=:evaluation AND h.id=:sheet AND h.estado='ANULADA'
                """).param("reason", reason).param("organization", organizationId)
                .param("evaluation", evaluationId).param("sheet", sheetId).update();
    }

    public Optional<String> sheetObservation(long organizationId, long evaluationId, long sheetId) {
        return db.sql("SELECT h.observacion FROM hojas_respuesta h JOIN evaluaciones e ON e.id=h.evaluacion_id "
                + "WHERE e.organizacion_id=:organization AND e.id=:evaluation AND h.id=:sheet")
                .param("organization", organizationId).param("evaluation", evaluationId).param("sheet", sheetId)
                .query(String.class).optional();
    }

    public long saveEvidence(long reviewId, String filename, String contentType, byte[] bytes, long userId) {
        long id = db.sql("INSERT INTO evidencias_revision(revision_id,nombre_original,tipo_contenido,tamanio_bytes,subida_por) VALUES (:review,:filename,:contentType,:size,:user) RETURNING id")
                .param("review", reviewId).param("filename", filename).param("contentType", contentType)
                .param("size", bytes.length).param("user", userId).query(Long.class).single();
        db.sql("INSERT INTO evidencias_contenido(evidencia_id,contenido) VALUES (:id,:content)")
                .param("id", id).param("content", bytes).update();
        return id;
    }

    public List<Evidence> evidence(long organizationId, long evaluationId, long reviewId) {
        return db.sql("SELECT ev.id,ev.revision_id,ev.nombre_original,ev.tipo_contenido,ev.tamanio_bytes,u.nombre,ev.subida_en FROM evidencias_revision ev LEFT JOIN usuarios u ON u.id=ev.subida_por JOIN revisiones r ON r.id=ev.revision_id JOIN hojas_respuesta h ON h.id=r.hoja_id JOIN evaluaciones e ON e.id=h.evaluacion_id WHERE e.organizacion_id=:organization AND e.id=:evaluation AND r.id=:review ORDER BY ev.subida_en")
                .param("organization", organizationId).param("evaluation", evaluationId).param("review", reviewId)
                .query((rs, row) -> new Evidence(rs.getLong(1), rs.getLong(2), rs.getString(3), rs.getString(4), rs.getLong(5),rs.getString(6),rs.getObject(7,OffsetDateTime.class))).list();
    }

    public Optional<EvidenceFile> file(long organizationId, long evaluationId, long evidenceId) {
        return db.sql("SELECT ev.id,ev.revision_id,ev.nombre_original,ev.tipo_contenido,ev.tamanio_bytes,ec.contenido,u.nombre,ev.subida_en FROM evidencias_revision ev LEFT JOIN usuarios u ON u.id=ev.subida_por JOIN evidencias_contenido ec ON ec.evidencia_id=ev.id JOIN revisiones r ON r.id=ev.revision_id JOIN hojas_respuesta h ON h.id=r.hoja_id JOIN evaluaciones e ON e.id=h.evaluacion_id WHERE e.organizacion_id=:organization AND e.id=:evaluation AND ev.id=:evidence")
                .param("organization", organizationId).param("evaluation", evaluationId).param("evidence", evidenceId)
                .query((rs, row) -> new EvidenceFile(new Evidence(rs.getLong(1), rs.getLong(2), rs.getString(3),
                        rs.getString(4), rs.getLong(5),rs.getString(7),rs.getObject(8,OffsetDateTime.class)), rs.getBytes(6))).optional();
    }

    public int deleteEvidence(long organizationId, long evaluationId, long evidenceId) {
        return db.sql("""
                DELETE FROM evidencias_revision ev WHERE ev.id=:evidence AND EXISTS (
                    SELECT 1 FROM revisiones r JOIN hojas_respuesta h ON h.id=r.hoja_id
                    JOIN evaluaciones e ON e.id=h.evaluacion_id
                    WHERE r.id=ev.revision_id AND e.organizacion_id=:organization AND e.id=:evaluation
                )
                """).param("evidence", evidenceId).param("organization", organizationId)
                .param("evaluation", evaluationId).update();
    }
}
