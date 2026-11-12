package com.admision.lector_dbf.report.repository;

import com.admision.lector_dbf.report.model.AnalyticsModels.*;
import com.admision.lector_dbf.shared.PageResult;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import javax.sql.DataSource;
import java.util.List;
import java.util.function.Consumer;

@Repository
public class AnalyticsRepository {
    // La inscripción es la base para contar también personas sin examen y grupos vacíos.
    private static final String BASE = """
            WITH base AS (
                SELECT i.id AS registration_id,i.grupo_id,h.id AS sheet_id,h.estado,h.formulario_id,
                       h.respuestas,r.id AS result_id,r.clave_id,coalesce(r.nota_oficial,r.puntaje) AS puntaje,r.aprobado
                FROM inscripciones i JOIN evaluaciones e ON e.id=i.evaluacion_id
                LEFT JOIN hojas_respuesta h ON h.inscripcion_id=i.id
                LEFT JOIN resultados r ON r.hoja_id=h.id
                WHERE e.organizacion_id=:organization AND e.id=:evaluation
                  AND (CAST(:groupId AS bigint) IS NULL OR coalesce(i.grupo_id,0)=:groupId)
                  AND (CAST(:formId AS bigint) IS NULL OR h.formulario_id=:formId)
            )
            """;

    private static final String GROUPS = BASE + """
            , definitions AS (
                SELECT id AS group_id,codigo AS code,nombre AS name FROM grupos_evaluacion
                WHERE evaluacion_id=:evaluation AND (CAST(:groupId AS bigint) IS NULL OR id=:groupId)
                UNION ALL SELECT NULL,'','Sin grupo' WHERE EXISTS (SELECT 1 FROM base WHERE grupo_id IS NULL)
            ), grouped AS (
                SELECT d.group_id,d.code,d.name,count(b.registration_id) AS registrations,
                       count(b.sheet_id) AS sheets,
                       count(b.result_id) FILTER (WHERE b.estado='VALIDADA') AS scored,
                       count(*) FILTER (WHERE b.estado='OBSERVADA') AS observed,
                       count(*) FILTER (WHERE b.estado='ANULADA') AS annulled,
                       count(*) FILTER (WHERE b.estado='VALIDADA' AND b.aprobado IS TRUE) AS passed,
                       count(*) FILTER (WHERE b.estado='VALIDADA' AND b.aprobado IS FALSE) AS failed,
                       count(b.result_id) FILTER (WHERE b.estado='VALIDADA' AND b.aprobado IS NULL) AS undecided,
                       round(avg(b.puntaje) FILTER (WHERE b.estado='VALIDADA'),4) AS average,
                       min(b.puntaje) FILTER (WHERE b.estado='VALIDADA') AS minimum,
                       max(b.puntaje) FILTER (WHERE b.estado='VALIDADA') AS maximum
                FROM definitions d LEFT JOIN base b ON b.grupo_id IS NOT DISTINCT FROM d.group_id
                GROUP BY d.group_id,d.code,d.name
            )
            SELECT * FROM grouped WHERE (lower(code) LIKE lower(:query) OR lower(name) LIKE lower(:query))
              AND (:mode='' OR (:mode='CON_RESULTADOS' AND scored>0) OR (:mode='SIN_RESULTADOS' AND scored=0))
            """;

    // Se compara con la clave referenciada por el resultado, nunca con la última clave del tema.
    private static final String QUESTIONS = BASE + """
            , answers AS (
                SELECT f.id AS form_id,f.codigo AS form_code,k.version AS key_version,
                       a.ordinality::integer AS question,coalesce(trim(a.answer),'') AS key_answer,
                       coalesce(trim(b.respuestas->>((a.ordinality-1)::integer)),'') AS response
                FROM base b JOIN claves_respuesta k ON k.id=b.clave_id
                JOIN formularios f ON f.id=b.formulario_id
                CROSS JOIN LATERAL jsonb_array_elements_text(k.respuestas) WITH ORDINALITY a(answer,ordinality)
                WHERE b.estado='VALIDADA' AND b.result_id IS NOT NULL
            ), counted AS (
                SELECT form_id,form_code,key_version,question,count(*) AS responses,
                    count(*) FILTER (WHERE key_answer='' OR upper(response)=upper(key_answer)) AS correct,
                    count(*) FILTER (WHERE key_answer<>'' AND response<>'' AND upper(response)<>upper(key_answer)) AS incorrect,
                    count(*) FILTER (WHERE key_answer<>'' AND response='') AS blank
                FROM answers GROUP BY form_id,form_code,key_version,question
            ), questions AS (
                SELECT *,round(100.0*incorrect/responses,2) AS incorrect_percent FROM counted
            )
            SELECT * FROM questions
            WHERE (lower(form_code) LIKE lower(:query) OR question::text LIKE :query)
              AND (:mode='' OR (:mode='CON_ERRORES' AND incorrect>0) OR (:mode='SIN_ERRORES' AND incorrect=0))
            """;

    private static final String RESULT_EXPORT = """
            SELECT h.id,i.codigo_externo AS codigo,concat_ws(' ',p.nombres,p.apellidos) AS participant,g.nombre AS group_name,
                   f.codigo AS form_code,h.estado,r.correctas,r.incorrectas,r.blancas,
                   coalesce(r.nota_oficial,r.puntaje) AS puntaje,r.posicion,r.aprobado,
                   k.version AS key_version,rc.version AS rule_version,
                   (SELECT max(v.version) FROM versiones_resultado v WHERE v.resultado_id=r.id) AS score_version
            FROM hojas_respuesta h JOIN evaluaciones e ON e.id=h.evaluacion_id
            JOIN inscripciones i ON i.id=h.inscripcion_id JOIN participantes p ON p.id=i.participante_id
            JOIN formularios f ON f.id=h.formulario_id LEFT JOIN grupos_evaluacion g ON g.id=i.grupo_id
            LEFT JOIN resultados r ON r.hoja_id=h.id LEFT JOIN claves_respuesta k ON k.id=r.clave_id
            LEFT JOIN reglas_calificacion rc ON rc.id=r.regla_id
            WHERE e.organizacion_id=:organization AND e.id=:evaluation
              AND (CAST(:groupId AS bigint) IS NULL OR coalesce(i.grupo_id,0)=:groupId)
              AND (CAST(:formId AS bigint) IS NULL OR h.formulario_id=:formId)
            ORDER BY h.id
            """;

    private static final RowMapper<GroupStats> GROUP_MAPPER = (rs, row) -> new GroupStats(
            (Long) rs.getObject("group_id"),rs.getString("code"),rs.getString("name"),
            rs.getLong("registrations"),rs.getLong("sheets"),rs.getLong("scored"),rs.getLong("observed"),
            rs.getLong("annulled"),rs.getLong("passed"),rs.getLong("failed"),rs.getLong("undecided"),
            rs.getBigDecimal("average"),rs.getBigDecimal("minimum"),rs.getBigDecimal("maximum"));
    private static final RowMapper<QuestionStats> QUESTION_MAPPER = (rs, row) -> new QuestionStats(
            rs.getLong("form_id"),rs.getString("form_code"),rs.getInt("key_version"),rs.getInt("question"),
            rs.getLong("responses"),rs.getLong("correct"),rs.getLong("incorrect"),rs.getLong("blank"),
            rs.getBigDecimal("incorrect_percent"));

    private final JdbcClient db;
    private final NamedParameterJdbcTemplate cursor;

    public AnalyticsRepository(JdbcClient db, DataSource source) {
        this.db = db;
        JdbcTemplate template = new JdbcTemplate(source);
        template.setFetchSize(500);
        this.cursor = new NamedParameterJdbcTemplate(template);
    }

    public Summary summary(Filter filter) {
        return bind(BASE + """
                SELECT count(*) AS registrations,count(sheet_id) AS sheets,
                    count(*) FILTER (WHERE estado='VALIDADA') AS valid,
                    count(*) FILTER (WHERE estado='OBSERVADA') AS observed,
                    count(*) FILTER (WHERE estado='ANULADA') AS annulled,
                    count(*) FILTER (WHERE estado='RECIBIDA') AS received,
                    count(result_id) FILTER (WHERE estado='VALIDADA') AS scored,
                    count(*) FILTER (WHERE estado='VALIDADA' AND aprobado IS TRUE) AS passed,
                    count(*) FILTER (WHERE estado='VALIDADA' AND aprobado IS FALSE) AS failed,
                    count(result_id) FILTER (WHERE estado='VALIDADA' AND aprobado IS NULL) AS undecided,
                    round(avg(puntaje) FILTER (WHERE estado='VALIDADA'),4) AS average,
                    (percentile_cont(0.5) WITHIN GROUP (ORDER BY puntaje)
                        FILTER (WHERE estado='VALIDADA'))::numeric AS median,
                    min(puntaje) FILTER (WHERE estado='VALIDADA') AS minimum,
                    max(puntaje) FILTER (WHERE estado='VALIDADA') AS maximum,
                    round(stddev_pop(puntaje) FILTER (WHERE estado='VALIDADA'),4) AS deviation
                FROM base
                """, filter).query((rs, row) -> new Summary(rs.getLong("registrations"),rs.getLong("sheets"),
                        rs.getLong("valid"),rs.getLong("observed"),rs.getLong("annulled"),rs.getLong("received"),
                        rs.getLong("scored"),rs.getLong("passed"),rs.getLong("failed"),rs.getLong("undecided"),
                        rs.getBigDecimal("average"),rs.getBigDecimal("median"),rs.getBigDecimal("minimum"),
                        rs.getBigDecimal("maximum"),rs.getBigDecimal("deviation"))).single();
    }

    public List<ScoreBin> distribution(Filter filter) {
        return bind(BASE + """
                , valid AS (SELECT result_id,puntaje FROM base WHERE estado='VALIDADA' AND result_id IS NOT NULL),
                bounds AS (SELECT min(puntaje) AS lo,max(puntaje) AS hi FROM valid),
                bucketed AS (SELECT least(7,coalesce(floor((v.puntaje-b.lo)*8/nullif(b.hi-b.lo,0)),0))::integer AS bucket,
                    count(*) AS total FROM valid v CROSS JOIN bounds b GROUP BY bucket),
                slots AS (SELECT generate_series(0,CASE WHEN lo=hi THEN 0 ELSE 7 END) AS bucket,lo,hi
                    FROM bounds WHERE lo IS NOT NULL)
                SELECT s.bucket,s.lo+(s.hi-s.lo)*s.bucket/8 AS lower,
                    CASE WHEN s.lo=s.hi THEN s.hi ELSE s.lo+(s.hi-s.lo)*(s.bucket+1)/8 END AS upper,
                    coalesce(b.total,0) AS total FROM slots s LEFT JOIN bucketed b ON b.bucket=s.bucket ORDER BY s.bucket
                """, filter).query((rs, row) -> new ScoreBin(rs.getInt(1),rs.getBigDecimal(2),
                        rs.getBigDecimal(3),rs.getLong(4))).list();
    }

    public PageResult<GroupStats> groups(Filter filter, String query, String mode, int page, int size) {
        long total = bind("SELECT count(*) FROM (" + GROUPS + ") counted", filter)
                .param("query", search(query)).param("mode", mode).query(Long.class).single();
        List<GroupStats> items = bind(GROUPS + " ORDER BY average DESC NULLS LAST,name,group_id LIMIT :size OFFSET :offset", filter)
                .param("query", search(query)).param("mode", mode).param("size", size).param("offset", page * size)
                .query(GROUP_MAPPER).list();
        return PageResult.of(items,page,size,total);
    }

    public PageResult<QuestionStats> questions(Filter filter, String query, String mode, String order, int page, int size) {
        long total = bind("SELECT count(*) FROM (" + QUESTIONS + ") counted", filter)
                .param("query", search(query)).param("mode", mode).query(Long.class).single();
        List<QuestionStats> items = bind(QUESTIONS + questionOrder(order) + " LIMIT :size OFFSET :offset", filter)
                .param("query", search(query)).param("mode", mode).param("size", size).param("offset", page * size)
                .query(QUESTION_MAPPER).list();
        return PageResult.of(items,page,size,total);
    }

    public void eachGroup(Filter filter, String query, String mode, Consumer<GroupStats> consumer) {
        cursor.query(GROUPS + " ORDER BY average DESC NULLS LAST,name,group_id", parameters(filter)
                .addValue("query",search(query)).addValue("mode",mode),
                (RowCallbackHandler) rs -> consumer.accept(GROUP_MAPPER.mapRow(rs,0)));
    }

    public void eachQuestion(Filter filter, String query, String mode, String order, Consumer<QuestionStats> consumer) {
        cursor.query(QUESTIONS + questionOrder(order), parameters(filter)
                .addValue("query",search(query)).addValue("mode",mode),
                (RowCallbackHandler) rs -> consumer.accept(QUESTION_MAPPER.mapRow(rs,0)));
    }

    public void eachResult(Filter filter, Consumer<ExportResult> consumer) {
        cursor.query(RESULT_EXPORT,parameters(filter),(RowCallbackHandler) rs -> consumer.accept(new ExportResult(
                rs.getLong("id"),rs.getString("codigo"),rs.getString("participant"),rs.getString("group_name"),
                rs.getString("form_code"),rs.getString("estado"),(Integer) rs.getObject("correctas"),
                (Integer) rs.getObject("incorrectas"),(Integer) rs.getObject("blancas"),rs.getBigDecimal("puntaje"),
                (Integer) rs.getObject("posicion"),(Boolean) rs.getObject("aprobado"),
                (Integer) rs.getObject("key_version"),(Integer) rs.getObject("rule_version"),
                (Integer) rs.getObject("score_version"))));
    }

    public boolean groupExists(Filter filter) {
        return db.sql("SELECT EXISTS(SELECT 1 FROM grupos_evaluacion WHERE evaluacion_id=:evaluation AND id=:groupId)")
                .param("evaluation",filter.evaluationId()).param("groupId",filter.groupId()).query(Boolean.class).single();
    }

    private JdbcClient.StatementSpec bind(String sql, Filter filter) {
        return db.sql(sql).param("organization",filter.organizationId()).param("evaluation",filter.evaluationId())
                .param("groupId",filter.groupId()).param("formId",filter.formId());
    }

    private MapSqlParameterSource parameters(Filter filter) {
        return new MapSqlParameterSource().addValue("organization",filter.organizationId())
                .addValue("evaluation",filter.evaluationId()).addValue("groupId",filter.groupId())
                .addValue("formId",filter.formId());
    }

    private String search(String query) { return "%" + query.trim() + "%"; }
    private String questionOrder(String order) {
        return "pregunta".equals(order) ? " ORDER BY form_code,question,key_version,form_id"
                : " ORDER BY incorrect_percent DESC,incorrect DESC,form_code,question,key_version,form_id";
    }
}
