package com.admision.lector_dbf.evaluation.repository;

import com.admision.lector_dbf.evaluation.model.EvaluationModels.*;
import com.admision.lector_dbf.shared.ApiException;
import com.admision.lector_dbf.shared.PageResult;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.Date;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public class EvaluationRepository {
    private final JdbcClient db;

    public EvaluationRepository(JdbcClient db) { this.db = db; }

    public long create(long organizationId, String code, String name, String description, String type, LocalDate date, String groupType) {
        return db.sql("INSERT INTO evaluaciones(organizacion_id,codigo,nombre,descripcion,tipo,fecha_evaluacion,tipo_grupo) VALUES (:organization,:code,:name,:description,:type,:date,:groupType) RETURNING id")
                .param("organization", organizationId).param("code", code).param("name", name)
                .param("description", description).param("type", type).param("date", date).param("groupType", groupType)
                .query(Long.class).single();
    }

    public List<Evaluation> list(long organizationId) {
        return db.sql("SELECT id,organizacion_id,codigo,nombre,descripcion,tipo,estado,fecha_evaluacion,tipo_grupo FROM evaluaciones WHERE organizacion_id=:organization ORDER BY creada_en DESC,id DESC")
                .param("organization", organizationId).query((rs, row) -> mapEvaluation(rs)).list();
    }

    public PageResult<Overview> page(long organizationId, String query, String status, int page, int size) {
        String search = "%" + query.trim() + "%";
        String filter = " WHERE organizacion_id=:organization AND (:status='' OR estado=:status) "
                + "AND (lower(codigo) LIKE lower(:query) OR lower(nombre) LIKE lower(:query) OR lower(tipo) LIKE lower(:query))";
        long total = db.sql("SELECT count(*) FROM evaluaciones" + filter)
                .param("organization", organizationId).param("status", status).param("query", search)
                .query(Long.class).single();
        List<Overview> items = db.sql("""
                WITH selected AS (
                    SELECT * FROM evaluaciones
                """ + filter + """
                    ORDER BY creada_en DESC,id DESC LIMIT :size OFFSET :offset
                )
                SELECT e.id,e.organizacion_id,e.codigo,e.nombre,e.descripcion,e.tipo,e.estado,e.fecha_evaluacion,e.tipo_grupo,
                       (SELECT count(*) FROM inscripciones i WHERE i.evaluacion_id=e.id) AS participantes,
                       (SELECT count(*) FROM hojas_respuesta h WHERE h.evaluacion_id=e.id) AS examenes,
                       (SELECT count(*) FROM hojas_respuesta h WHERE h.evaluacion_id=e.id AND h.estado='OBSERVADA') AS observados,
                       (SELECT count(*) FROM resultados r JOIN hojas_respuesta h ON h.id=r.hoja_id
                        WHERE h.evaluacion_id=e.id AND h.estado='VALIDADA' AND r.aprobado) AS ingresantes,
                       (SELECT avg(r.puntaje) FROM resultados r JOIN hojas_respuesta h ON h.id=r.hoja_id
                        WHERE h.evaluacion_id=e.id AND h.estado='VALIDADA') AS promedio
                FROM selected e ORDER BY e.creada_en DESC,e.id DESC
                """).param("organization", organizationId).param("status", status).param("query", search)
                .param("size", size).param("offset", page * size)
                .query((rs, row) -> new Overview(rs.getLong("id"), rs.getLong("organizacion_id"),
                        rs.getString("codigo"), rs.getString("nombre"), rs.getString("descripcion"),
                        rs.getString("tipo"), rs.getString("estado"),
                        rs.getDate("fecha_evaluacion") == null ? null : rs.getDate("fecha_evaluacion").toLocalDate(), rs.getString("tipo_grupo"),
                        rs.getLong("participantes"), rs.getLong("examenes"), rs.getLong("observados"),
                        rs.getLong("ingresantes"), rs.getBigDecimal("promedio"))).list();
        return PageResult.of(items, page, size, total);
    }

    public Overview overview(long organizationId, long evaluationId) {
        return db.sql("""
                SELECT e.id,e.organizacion_id,e.codigo,e.nombre,e.descripcion,e.tipo,e.estado,e.fecha_evaluacion,e.tipo_grupo,
                       (SELECT count(*) FROM inscripciones i WHERE i.evaluacion_id=e.id) AS participantes,
                       (SELECT count(*) FROM hojas_respuesta h WHERE h.evaluacion_id=e.id) AS examenes,
                       (SELECT count(*) FROM hojas_respuesta h WHERE h.evaluacion_id=e.id AND h.estado='OBSERVADA') AS observados,
                       (SELECT count(*) FROM resultados r JOIN hojas_respuesta h ON h.id=r.hoja_id
                        WHERE h.evaluacion_id=e.id AND h.estado='VALIDADA' AND r.aprobado) AS ingresantes,
                       (SELECT avg(r.puntaje) FROM resultados r JOIN hojas_respuesta h ON h.id=r.hoja_id
                        WHERE h.evaluacion_id=e.id AND h.estado='VALIDADA') AS promedio
                FROM evaluaciones e WHERE e.organizacion_id=:organization AND e.id=:evaluation
                """).param("organization", organizationId).param("evaluation", evaluationId)
                .query((rs, row) -> new Overview(rs.getLong("id"), rs.getLong("organizacion_id"),
                        rs.getString("codigo"), rs.getString("nombre"), rs.getString("descripcion"),
                        rs.getString("tipo"), rs.getString("estado"),
                        rs.getDate("fecha_evaluacion") == null ? null : rs.getDate("fecha_evaluacion").toLocalDate(), rs.getString("tipo_grupo"),
                        rs.getLong("participantes"), rs.getLong("examenes"), rs.getLong("observados"),
                        rs.getLong("ingresantes"), rs.getBigDecimal("promedio")))
                .optional().orElseThrow(() -> ApiException.notFound("Evaluación"));
    }

    public Statistics statistics(long organizationId) {
        return db.sql("""
                SELECT (SELECT count(*) FROM evaluaciones WHERE organizacion_id=:organization),
                       (SELECT count(*) FROM hojas_respuesta h JOIN evaluaciones e ON e.id=h.evaluacion_id
                        WHERE e.organizacion_id=:organization),
                       (SELECT count(*) FROM hojas_respuesta h JOIN evaluaciones e ON e.id=h.evaluacion_id
                        WHERE e.organizacion_id=:organization AND h.estado='OBSERVADA'),
                       (SELECT count(*) FROM evaluaciones WHERE organizacion_id=:organization AND estado='PUBLICADA')
                """).param("organization", organizationId)
                .query((rs, row) -> new Statistics(rs.getLong(1), rs.getLong(2), rs.getLong(3), rs.getLong(4))).single();
    }

    public Optional<Evaluation> find(long organizationId, long id) {
        return db.sql("SELECT id,organizacion_id,codigo,nombre,descripcion,tipo,estado,fecha_evaluacion,tipo_grupo FROM evaluaciones WHERE organizacion_id=:organization AND id=:id")
                .param("organization", organizationId).param("id", id).query((rs, row) -> mapEvaluation(rs)).optional();
    }

    private Evaluation mapEvaluation(java.sql.ResultSet rs) throws java.sql.SQLException {
        Date date = rs.getDate("fecha_evaluacion");
        return new Evaluation(rs.getLong("id"), rs.getLong("organizacion_id"), rs.getString("codigo"),
                rs.getString("nombre"), rs.getString("descripcion"), rs.getString("tipo"),
                rs.getString("estado"), date == null ? null : date.toLocalDate(), rs.getString("tipo_grupo"));
    }

    public int update(long organizationId, long id, String name, String description, LocalDate date, String groupType) {
        return db.sql("UPDATE evaluaciones SET nombre=:name,descripcion=:description,fecha_evaluacion=:date,tipo_grupo=:groupType,actualizada_en=now() WHERE organizacion_id=:organization AND id=:id AND estado IN ('BORRADOR','CONFIGURADA')")
                .param("name", name).param("description", description).param("date", date).param("groupType", groupType)
                .param("organization", organizationId).param("id", id).update();
    }

    public int status(long organizationId, long id, String status) {
        return db.sql("UPDATE evaluaciones SET estado=:status,actualizada_en=now() WHERE organizacion_id=:organization AND id=:id")
                .param("status", status).param("organization", organizationId).param("id", id).update();
    }

    public long createGroup(long evaluationId, String code, String name, Integer capacity) {
        return db.sql("INSERT INTO grupos_evaluacion(evaluacion_id,codigo,nombre,cupo) VALUES (:evaluation,:code,:name,:capacity) RETURNING id")
                .param("evaluation", evaluationId).param("code", code).param("name", name).param("capacity", capacity).query(Long.class).single();
    }

    public List<Group> groups(long evaluationId) {
        return db.sql("SELECT id,evaluacion_id,codigo,nombre,cupo FROM grupos_evaluacion WHERE evaluacion_id=:evaluation ORDER BY nombre")
                .param("evaluation", evaluationId)
                .query((rs, row) -> new Group(rs.getLong(1), rs.getLong(2), rs.getString(3), rs.getString(4), (Integer) rs.getObject(5))).list();
    }

    public int updateCapacity(long evaluationId, long groupId, Integer capacity) {
        return db.sql("UPDATE grupos_evaluacion SET cupo=:capacity WHERE evaluacion_id=:evaluation AND id=:groupId")
                .param("capacity", capacity).param("evaluation", evaluationId).param("groupId", groupId).update();
    }

    public int updateGroup(long evaluationId, long groupId, String code, String name, Integer capacity) {
        return db.sql("UPDATE grupos_evaluacion SET codigo=:code,nombre=:name,cupo=:capacity WHERE evaluacion_id=:evaluation AND id=:groupId")
                .param("code", code).param("name", name).param("capacity", capacity)
                .param("evaluation", evaluationId).param("groupId", groupId).update();
    }

    public long createForm(long evaluationId, String code, int questions, int options) {
        return db.sql("INSERT INTO formularios(evaluacion_id,codigo,numero_preguntas,opciones_por_pregunta) VALUES (:evaluation,:code,:questions,:options) RETURNING id")
                .param("evaluation", evaluationId).param("code", code).param("questions", questions).param("options", options).query(Long.class).single();
    }

    public List<Form> forms(long evaluationId) {
        return db.sql("SELECT id,evaluacion_id,codigo,numero_preguntas,opciones_por_pregunta FROM formularios WHERE evaluacion_id=:evaluation ORDER BY codigo")
                .param("evaluation", evaluationId)
                .query((rs, row) -> new Form(rs.getLong(1), rs.getLong(2), rs.getString(3), rs.getInt(4), rs.getInt(5))).list();
    }

    public Optional<Form> form(long evaluationId, long formId) {
        return forms(evaluationId).stream().filter(form -> form.id() == formId).findFirst();
    }

    public long createKey(long formId, String answers, long userId) {
        return db.sql("INSERT INTO claves_respuesta(formulario_id,version,respuestas,creada_por) SELECT :form,coalesce(max(version),0)+1,CAST(:answers AS jsonb),:user FROM claves_respuesta WHERE formulario_id=:form RETURNING id")
                .param("form", formId).param("answers", answers).param("user", userId).query(Long.class).single();
    }

    public Optional<AnswerKey> latestKey(long formId) {
        return db.sql("SELECT id,formulario_id,version,respuestas::text FROM claves_respuesta WHERE formulario_id=:form ORDER BY version DESC LIMIT 1")
                .param("form", formId)
                .query((rs, row) -> new AnswerKey(rs.getLong(1), rs.getLong(2), rs.getInt(3), rs.getString(4))).optional();
    }

    public long createRule(long evaluationId, BigDecimal correct, BigDecimal incorrect,
                           BigDecimal blank, BigDecimal passingScore, long userId) {
        return db.sql("INSERT INTO reglas_calificacion(evaluacion_id,version,puntos_correcta,puntos_incorrecta,puntos_blanca,minima_aprobacion,creada_por) SELECT :evaluation,coalesce(max(version),0)+1,:correct,:incorrect,:blank,:passing,:user FROM reglas_calificacion WHERE evaluacion_id=:evaluation RETURNING id")
                .param("evaluation", evaluationId).param("correct", correct).param("incorrect", incorrect)
                .param("blank", blank).param("passing", passingScore).param("user", userId).query(Long.class).single();
    }

    public Optional<Rule> latestRule(long evaluationId) {
        return db.sql("SELECT id,evaluacion_id,version,puntos_correcta,puntos_incorrecta,puntos_blanca,minima_aprobacion FROM reglas_calificacion WHERE evaluacion_id=:evaluation ORDER BY version DESC LIMIT 1")
                .param("evaluation", evaluationId).query((rs, row) -> new Rule(rs.getLong(1), rs.getLong(2), rs.getInt(3),
                        rs.getBigDecimal(4), rs.getBigDecimal(5), rs.getBigDecimal(6), rs.getBigDecimal(7))).optional();
    }

    public long createParticipant(long organizationId, String code, String names, String surnames, String document) {
        return db.sql("INSERT INTO participantes(organizacion_id,codigo,nombres,apellidos,documento) VALUES (:organization,:code,:names,:surnames,:document) RETURNING id")
                .param("organization", organizationId).param("code", code).param("names", names)
                .param("surnames", surnames).param("document", document).query(Long.class).single();
    }

    public Optional<ParticipantWrite> registerParticipant(long org,String code,String names,String surnames,String document,String email) {
        Optional<Long> inserted=db.sql("INSERT INTO participantes(organizacion_id,codigo,nombres,apellidos,documento,correo) "
                + "VALUES (:org,:code,:names,:surnames,:document,:email) ON CONFLICT (organizacion_id,codigo) DO NOTHING RETURNING id")
                .param("org",org).param("code",code).param("names",names).param("surnames",surnames)
                .param("document",document).param("email",email).query(Long.class).optional();
        if (inserted.isPresent()) return inserted.map(id -> new ParticipantWrite(id,true));
        return db.sql("UPDATE participantes SET nombres=:names,apellidos=:surnames,documento=:document,correo=:email,"
                + "registro_automatico=false WHERE organizacion_id=:org AND codigo=:code AND registro_automatico "
                + "AND nombres=codigo AND nullif(trim(apellidos),'') IS NULL AND nullif(trim(documento),'') IS NULL "
                + "AND nullif(trim(correo),'') IS NULL RETURNING id")
                .param("org",org).param("code",code).param("names",names).param("surnames",surnames)
                .param("document",document).param("email",email).query(Long.class).optional()
                .map(id -> new ParticipantWrite(id,false));
    }

    public long createAutomaticParticipant(long org,String code) {
        db.sql("INSERT INTO participantes(organizacion_id,codigo,nombres,registro_automatico) VALUES (:org,:code,:code,true) "
                + "ON CONFLICT (organizacion_id,codigo) DO NOTHING").param("org",org).param("code",code).update();
        return participantByCode(org,code).orElseThrow().id();
    }

    private Participant mapParticipant(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new Participant(rs.getLong("id"),rs.getLong("organizacion_id"),rs.getString("codigo"),
                rs.getString("nombres"),rs.getString("apellidos"),rs.getString("documento"),rs.getString("correo"),rs.getBoolean("registro_automatico"));
    }

    public List<Participant> participants(long organizationId) {
        return db.sql("SELECT * FROM participantes WHERE organizacion_id=:organization ORDER BY id DESC")
                .param("organization", organizationId).query((rs, row) -> mapParticipant(rs)).list();
    }

    public PageResult<Participant> participantsPage(long organizationId, String query, String document,
                                                    int page, int size) {
        return participantsPage(organizationId,query,document,page,size,"");
    }
    public PageResult<Participant> participantsPage(long organizationId,String query,String document,int page,int size,String profile) {
        String filter = " FROM participantes WHERE organizacion_id=:organization "
                + "AND (lower(codigo) LIKE lower(:query) OR lower(nombres) LIKE lower(:query) "
                + "OR lower(coalesce(apellidos,'')) LIKE lower(:query) OR lower(coalesce(documento,'')) LIKE lower(:query) OR lower(coalesce(correo,'')) LIKE lower(:query)) "
                + "AND (:document='' OR (:document='CON_DOCUMENTO' AND length(trim(coalesce(documento,''))) > 0) "
                + "OR (:document='SIN_DOCUMENTO' AND length(trim(coalesce(documento,''))) = 0)) "
                + "AND (:profile='' OR (:profile='AUTOMATICOS' AND registro_automatico) "
                + "OR (:profile='COMPLETOS' AND NOT registro_automatico) "
                + "OR (:profile='SIN_CORREO' AND nullif(trim(correo),'') IS NULL))";
        String search = "%" + query.trim() + "%";
        long total = db.sql("SELECT count(*)" + filter).param("organization", organizationId)
                .param("query", search).param("document", document).param("profile",profile).query(Long.class).single();
        List<Participant> items = db.sql("SELECT *"
                        + filter + " ORDER BY id DESC LIMIT :size OFFSET :offset")
                .param("organization", organizationId).param("query", search).param("document", document).param("profile",profile)
                .param("size", size).param("offset", page * size)
                .query((rs, row) -> mapParticipant(rs)).list();
        return PageResult.of(items, page, size, total);
    }

    public int updateParticipant(long organizationId, long participantId, String names, String surnames, String document,String email) {
        return db.sql("UPDATE participantes SET nombres=:names,apellidos=:surnames,documento=:document,correo=:email,registro_automatico=false "
                        + "WHERE organizacion_id=:organization AND id=:participant")
                .param("names", names).param("surnames", surnames).param("document", document).param("email",email)
                .param("organization", organizationId).param("participant", participantId).update();
    }

    public Optional<Participant> participantByCode(long organizationId, String code) {
        return db.sql("SELECT * FROM participantes WHERE organizacion_id=:organization AND codigo=:code")
                .param("organization", organizationId).param("code", code)
                .query((rs, row) -> mapParticipant(rs)).optional();
    }

    public long register(long organizationId, long evaluationId, long participantId, Long groupId) {
        return db.sql("INSERT INTO inscripciones(organizacion_id,evaluacion_id,participante_id,grupo_id,codigo_externo) SELECT :organization,:evaluation,:participant,:groupId,codigo FROM participantes WHERE id=:participant AND organizacion_id=:organization RETURNING id")
                .param("organization", organizationId).param("evaluation", evaluationId)
                .param("participant", participantId).param("groupId", groupId).query(Long.class).single();
    }

    public boolean isRegistered(long organization,long evaluation,long participant) {
        return db.sql("SELECT EXISTS(SELECT 1 FROM inscripciones WHERE organizacion_id=:org AND evaluacion_id=:evaluation AND participante_id=:participant)")
                .param("org",organization).param("evaluation",evaluation).param("participant",participant).query(Boolean.class).single();
    }

    public boolean participantExists(long organization, long participant) {
        return db.sql("SELECT EXISTS(SELECT 1 FROM participantes WHERE organizacion_id=:org AND id=:participant)")
                .param("org", organization).param("participant", participant).query(Boolean.class).single();
    }

    public PageResult<Registration> registrationsPage(long organizationId, long evaluationId,
                                                      String query, Long groupId, int page, int size) {
        String filter = " FROM inscripciones i JOIN participantes p ON p.id=i.participante_id "
                + "LEFT JOIN grupos_evaluacion g ON g.id=i.grupo_id "
                + "WHERE i.organizacion_id=:organization AND i.evaluacion_id=:evaluation "
                + "AND (CAST(:groupId AS bigint) IS NULL OR i.grupo_id=:groupId) "
                + "AND (lower(i.codigo_externo) LIKE lower(:query) OR lower(p.nombres) LIKE lower(:query) "
                + "OR lower(coalesce(p.apellidos,'')) LIKE lower(:query))";
        String search = "%" + query.trim() + "%";
        long total = db.sql("SELECT count(*)" + filter)
                .param("organization", organizationId).param("evaluation", evaluationId)
                .param("groupId", groupId).param("query", search).query(Long.class).single();
        List<Registration> items = db.sql("SELECT i.id,p.id,i.codigo_externo,p.nombres,p.apellidos,g.id,g.nombre"
                        + filter + " ORDER BY i.codigo_externo,i.id LIMIT :size OFFSET :offset")
                .param("organization", organizationId).param("evaluation", evaluationId)
                .param("groupId", groupId).param("query", search).param("size", size).param("offset", page * size)
                .query((rs, row) -> new Registration(rs.getLong(1), rs.getLong(2), rs.getString(3),
                        rs.getString(4), rs.getString(5), (Long) rs.getObject(6), rs.getString(7))).list();
        return PageResult.of(items, page, size, total);
    }

    public int updateRegistrationGroup(long organizationId, long evaluationId, long registrationId, Long groupId) {
        return db.sql("UPDATE inscripciones SET grupo_id=:groupId WHERE organizacion_id=:organization "
                        + "AND evaluacion_id=:evaluation AND id=:registration")
                .param("groupId", groupId).param("organization", organizationId)
                .param("evaluation", evaluationId).param("registration", registrationId).update();
    }

    public Optional<Long> registrationByCode(long organizationId, long evaluationId, String code) {
        return db.sql("SELECT i.id FROM inscripciones i WHERE i.organizacion_id=:organization AND i.evaluacion_id=:evaluation AND i.codigo_externo=:code")
                .param("organization", organizationId).param("evaluation", evaluationId).param("code", code).query(Long.class).optional();
    }

    public long createAutomaticRegistration(long organization, long evaluation, String code) {
        long person = db.sql("INSERT INTO participantes(organizacion_id,codigo,nombres,registro_automatico) VALUES(:org,:reference,:code,true) RETURNING id")
                .param("org", organization).param("reference", "DBF-" + java.util.UUID.randomUUID()).param("code", code).query(Long.class).single();
        return db.sql("INSERT INTO inscripciones(organizacion_id,evaluacion_id,participante_id,codigo_externo) VALUES(:org,:evaluation,:person,:code) RETURNING id")
                .param("org", organization).param("evaluation", evaluation).param("person", person).param("code", code).query(Long.class).single();
    }

    public List<Long> registrationIds(long evaluationId) {
        return db.sql("SELECT id FROM inscripciones WHERE evaluacion_id=:evaluation")
                .param("evaluation", evaluationId).query(Long.class).list();
    }

    public Evaluation require(long organizationId, long id) {
        return find(organizationId, id).orElseThrow(() -> ApiException.notFound("Evaluación"));
    }
}
