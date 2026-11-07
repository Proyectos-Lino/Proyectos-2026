package com.admision.lector_dbf.evaluation.repository;

import com.admision.lector_dbf.evaluation.model.ParticipantImportRow;
import com.admision.lector_dbf.shared.*;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class ParticipantImportReportRepository {
    private final JdbcClient db;
    public ParticipantImportReportRepository(JdbcClient db) { this.db=db; }
    public long create(long organization,long user) {
        return db.sql("INSERT INTO importaciones_directorio(organizacion_id,registrada_por) VALUES(:org,:user) RETURNING id")
                .param("org",organization).param("user",user).query(Long.class).single();
    }
    public void row(long report,int row,String code,String status) {
        db.sql("INSERT INTO importaciones_directorio_filas(importacion_id,fila,codigo,estado) VALUES(:report,:row,:code,:status)")
                .param("report",report).param("row",row).param("code",code).param("status",status).update();
    }
    public PageResult<ParticipantImportRow> page(long organization,long report,String query,String status,int page,int size) {
        if (!db.sql("SELECT EXISTS(SELECT 1 FROM importaciones_directorio WHERE id=:id AND organizacion_id=:org)")
                .param("id",report).param("org",organization).query(Boolean.class).single()) throw ApiException.notFound("Informe de importación");
        String filter=" FROM importaciones_directorio_filas WHERE importacion_id=:report AND codigo ILIKE :query AND (:status='' OR estado=:status)";
        long total=db.sql("SELECT count(*)"+filter).param("report",report).param("query","%"+query.trim()+"%")
                .param("status",status).query(Long.class).single();
        var rows=db.sql("SELECT fila,codigo,estado"+filter+" ORDER BY fila LIMIT :size OFFSET :offset")
                .param("report",report).param("query","%"+query.trim()+"%").param("status",status)
                .param("size",size).param("offset",page*size)
                .query((rs,n)->new ParticipantImportRow(rs.getInt(1),rs.getString(2),rs.getString(3))).list();
        return PageResult.of(rows,page,size,total);
    }
}
