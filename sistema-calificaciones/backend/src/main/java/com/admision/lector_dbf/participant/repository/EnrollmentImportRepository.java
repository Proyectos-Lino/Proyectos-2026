package com.admision.lector_dbf.participant.repository;

import com.admision.lector_dbf.participant.model.ParticipantModels.ImportRow;
import com.admision.lector_dbf.shared.ApiException;
import com.admision.lector_dbf.shared.PageResult;
import java.util.List;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class EnrollmentImportRepository {
    private final JdbcClient db;
    public EnrollmentImportRepository(JdbcClient db) { this.db = db; }

    public long create(long org, long evaluation, long actor, String mode) {
        return db.sql("INSERT INTO importaciones_inscripciones(organizacion_id,evaluacion_id,registrada_por,modo) VALUES(:org,:evaluation,:actor,:mode) RETURNING id")
                .param("org", org).param("evaluation", evaluation).param("actor", actor).param("mode", mode).query(Long.class).single();
    }
    public void saveRow(long report, ImportRow row) {
        db.sql("INSERT INTO importaciones_inscripciones_filas(importacion_id,fila,codigo,accion,detalle,grupo_anterior,grupo_nuevo) VALUES(:report,:row,:code,:action,:detail,:before,:after)")
                .param("report", report).param("row", row.row()).param("code", row.code()).param("action", row.action())
                .param("detail", row.detail()).param("before", row.previousGroup()).param("after", row.group()).update();
    }
    public PageResult<ImportRow> page(long org, long evaluation, long report, String query, String action, int page, int size) {
        if (!db.sql("SELECT EXISTS(SELECT 1 FROM importaciones_inscripciones WHERE id=:report AND organizacion_id=:org AND evaluacion_id=:evaluation)")
                .param("report", report).param("org", org).param("evaluation", evaluation).query(Boolean.class).single())
            throw ApiException.notFound("Informe de importación");
        String filter = " FROM importaciones_inscripciones_filas WHERE importacion_id=:report AND (:action='' OR accion=:action) AND concat_ws(' ',codigo,detalle,grupo_anterior,grupo_nuevo) ILIKE :query";
        long total = db.sql("SELECT count(*)" + filter).param("report", report).param("action", action).param("query", "%" + query.trim() + "%").query(Long.class).single();
        List<ImportRow> rows = db.sql("SELECT fila,codigo,accion,detalle,grupo_anterior,grupo_nuevo" + filter + " ORDER BY fila LIMIT :size OFFSET :offset")
                .param("report", report).param("action", action).param("query", "%" + query.trim() + "%").param("size", size).param("offset", page * size)
                .query((rs, n) -> new ImportRow(rs.getInt(1), rs.getString(2), rs.getString(3), rs.getString(4), rs.getString(5), rs.getString(6))).list();
        return PageResult.of(rows, page, size, total);
    }
}
