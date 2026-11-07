package com.admision.lector_dbf.evaluation.repository;

import com.admision.lector_dbf.evaluation.model.ScoringModels.RankingPolicy;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class RankingPolicyRepository {
    private final JdbcClient db;
    public RankingPolicyRepository(JdbcClient db) { this.db = db; }

    public RankingPolicy createDefault(long evaluationId, String evaluationType, long userId) {
        String approval = "ADMISION".equalsIgnoreCase(evaluationType) ? "VACANTES_O_MINIMA" : "MINIMA";
        return save(evaluationId, "GRUPO", approval, "COMPARTIR_PUESTO", null, userId);
    }

    public RankingPolicy save(long evaluationId, String scope, String approvalMode,
                              String tieBreak, Integer globalCapacity, long userId) {
        long id = db.sql("INSERT INTO politicas_resultado(evaluacion_id,version,alcance_ranking,modo_aprobacion,desempate,cupo_global,creada_por) "
                + "SELECT :evaluation,coalesce(max(version),0)+1,:scope,:approval,:tie,:capacity,:user "
                + "FROM politicas_resultado WHERE evaluacion_id=:evaluation RETURNING id")
                .param("evaluation", evaluationId).param("scope", scope).param("approval", approvalMode)
                .param("tie", tieBreak).param("capacity", globalCapacity).param("user", userId)
                .query(Long.class).single();
        return latest(evaluationId).filter(policy -> policy.id() == id).orElseThrow();
    }

    public Optional<RankingPolicy> latest(long evaluationId) {
        return db.sql("SELECT id,evaluacion_id,version,alcance_ranking,modo_aprobacion,desempate,cupo_global "
                + "FROM politicas_resultado WHERE evaluacion_id=:evaluation ORDER BY version DESC LIMIT 1")
                .param("evaluation", evaluationId)
                .query((rs, row) -> new RankingPolicy(rs.getLong(1), rs.getLong(2), rs.getInt(3),
                        rs.getString(4), rs.getString(5), rs.getString(6), (Integer) rs.getObject(7)))
                .optional();
    }
}
