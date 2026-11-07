package com.admision.lector_dbf.audit.repository;

import com.admision.lector_dbf.audit.model.AuditEvent;
import com.admision.lector_dbf.shared.PageResult;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

@Repository
public class AuditRepository {
    private final JdbcClient db;
    private final ObjectMapper json;
    public AuditRepository(JdbcClient db, ObjectMapper json) { this.db = db; this.json = json; }

    public void record(long organizationId, long evaluationId, Long userId,
                       String action, String entity, Long entityId, Map<String, ?> details) {
        try {
            record(organizationId, evaluationId, userId, action, entity, entityId,
                    json.writeValueAsString(details));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("No se pudo registrar la auditoría", exception);
        }
    }

    public void record(long organizationId, long evaluationId, Long userId,
                       String action, String entity, Long entityId, String details) {
        db.sql("INSERT INTO eventos_auditoria(organizacion_id,evaluacion_id,usuario_id,accion,entidad,entidad_id,detalles) "
                + "VALUES (:organization,:evaluation,:user,:action,:entity,:entityId,CAST(:details AS jsonb))")
                .param("organization", organizationId).param("evaluation", evaluationId).param("user", userId)
                .param("action", action).param("entity", entity).param("entityId", entityId)
                .param("details", details).update();
    }

    public PageResult<AuditEvent> page(long organizationId, long evaluationId, int page, int size) {
        String source = " FROM eventos_auditoria a LEFT JOIN usuarios u ON u.id=a.usuario_id "
                + "WHERE a.organizacion_id=:organization AND a.evaluacion_id=:evaluation";
        long total = db.sql("SELECT count(*)" + source).param("organization", organizationId)
                .param("evaluation", evaluationId).query(Long.class).single();
        List<AuditEvent> items = db.sql("SELECT a.id,a.usuario_id,u.nombre,a.accion,a.entidad,a.entidad_id,"
                        + "a.detalles::text,a.ocurrido_en,CASE a.entidad "
                        + "WHEN 'EXAMEN' THEN (SELECT h.id FROM hojas_respuesta h WHERE h.id=a.entidad_id AND h.evaluacion_id=a.evaluacion_id) "
                        + "WHEN 'REVISION' THEN (SELECT r.hoja_id FROM revisiones r JOIN hojas_respuesta h ON h.id=r.hoja_id WHERE r.id=a.entidad_id AND h.evaluacion_id=a.evaluacion_id) "
                        + "WHEN 'EVIDENCIA' THEN (SELECT r.hoja_id FROM evidencias_revision ev JOIN revisiones r ON r.id=ev.revision_id JOIN hojas_respuesta h ON h.id=r.hoja_id WHERE ev.id=a.entidad_id AND h.evaluacion_id=a.evaluacion_id) END"
                        + source + " ORDER BY a.id DESC LIMIT :size OFFSET :offset")
                .param("organization", organizationId).param("evaluation", evaluationId)
                .param("size", size).param("offset", page * size)
                .query((rs, row) -> new AuditEvent(rs.getLong(1), (Long) rs.getObject(2), rs.getString(3),
                        rs.getString(4), rs.getString(5), (Long) rs.getObject(6), rs.getString(7),
                        rs.getObject(8, OffsetDateTime.class),(Long)rs.getObject(9))).list();
        return PageResult.of(items, page, size, total);
    }
}
