package com.admision.lector_dbf.evaluation.repository;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class PreparationRepository {
    private final JdbcClient db;
    public PreparationRepository(JdbcClient db) { this.db = db; }
    public long forms(long evaluation) {
        return db.sql("SELECT count(*) FROM formularios WHERE evaluacion_id=:evaluation")
                .param("evaluation", evaluation).query(Long.class).single();
    }
    public long missingKeys(long evaluation) {
        return db.sql("SELECT count(*) FROM formularios f WHERE f.evaluacion_id=:evaluation "
                + "AND NOT EXISTS(SELECT 1 FROM claves_respuesta k WHERE k.formulario_id=f.id)")
                .param("evaluation", evaluation).query(Long.class).single();
    }
    public long registrations(long evaluation) {
        return db.sql("SELECT count(*) FROM inscripciones WHERE evaluacion_id=:evaluation")
                .param("evaluation", evaluation).query(Long.class).single();
    }
}
