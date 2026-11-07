package com.admision.lector_dbf.participant.repository;

import com.admision.lector_dbf.participant.model.ParticipantModels.*;
import com.admision.lector_dbf.shared.ApiException;
import com.admision.lector_dbf.shared.PageResult;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class ParticipantRepository {
    private final JdbcClient db;
    private static final String PERSON_SELECT = "SELECT p.*, (SELECT count(*) FROM inscripciones i WHERE i.participante_id=p.id) AS registrations FROM participantes p ";
    private static final String ENROLLMENT_FROM = " FROM inscripciones i JOIN participantes p ON p.id=i.participante_id LEFT JOIN grupos_evaluacion g ON g.id=i.grupo_id ";
    private static final String ENROLLMENT_SELECT = "SELECT i.id,p.id AS person_id,i.codigo_externo,p.nombres,p.apellidos,p.tipo_documento,p.documento,p.correo,p.registro_automatico,i.grupo_id,g.nombre AS group_name,EXISTS(SELECT 1 FROM hojas_respuesta h WHERE h.inscripcion_id=i.id) AS has_exam";
    public ParticipantRepository(JdbcClient db) { this.db = db; }

    public void lockOrganization(long organization) {
        db.sql("SELECT id FROM organizaciones WHERE id=:org FOR UPDATE").param("org", organization).query(Long.class).single();
    }
    public Person person(long organization, long id) {
        return db.sql(PERSON_SELECT + "WHERE p.organizacion_id=:org AND p.id=:id")
                .param("org", organization).param("id", id).query(this::person).optional()
                .orElseThrow(() -> ApiException.notFound("Persona"));
    }
    public List<Person> byDocument(long organization, String type, String document) {
        return db.sql(PERSON_SELECT + "WHERE p.organizacion_id=:org AND p.tipo_documento=:type "
                + "AND upper(regexp_replace(trim(p.documento),'\\s+','','g'))=:document ORDER BY p.id")
                .param("org", organization).param("type", type).param("document", document).query(this::person).list();
    }
    public PageResult<Person> people(long organization, String query, int page, int size) {
        String where = "WHERE p.organizacion_id=:org AND NOT p.registro_automatico "
                + "AND concat_ws(' ',p.nombres,p.apellidos,p.documento,p.correo) ILIKE :query";
        String search = "%" + query.trim() + "%";
        long total = db.sql("SELECT count(*) FROM participantes p " + where).param("org", organization)
                .param("query", search).query(Long.class).single();
        var people = db.sql(PERSON_SELECT + where + " ORDER BY p.nombres,p.id LIMIT :size OFFSET :offset")
                .param("org", organization).param("query", search).param("size", size).param("offset", page * size)
                .query(this::person).list();
        return PageResult.of(people, page, size, total);
    }
    public long createPerson(long organization, String names, String surnames, String type, String document, String email, boolean automatic) {
        return db.sql("INSERT INTO participantes(organizacion_id,codigo,nombres,apellidos,tipo_documento,documento,correo,registro_automatico) "
                + "VALUES(:org,:reference,:names,:surnames,:type,:document,:email,:automatic) RETURNING id")
                .param("org", organization).param("reference", "PERSONA-" + UUID.randomUUID()).param("names", names)
                .param("surnames", surnames).param("type", type).param("document", document).param("email", email)
                .param("automatic", automatic).query(Long.class).single();
    }
    public void updatePerson(long organization, long id, String names, String surnames, String type,
                             String document, String email, boolean automatic) {
        db.sql("UPDATE participantes SET nombres=:names,apellidos=:surnames,tipo_documento=:type,documento=:document,correo=:email,registro_automatico=:automatic WHERE organizacion_id=:org AND id=:id")
                .param("org", organization).param("id", id).param("names", names).param("surnames", surnames)
                .param("type", type).param("document", document).param("email", email).param("automatic", automatic).update();
    }
    public Optional<Enrollment> byCode(long organization, long evaluation, String code) {
        return db.sql(ENROLLMENT_SELECT + ENROLLMENT_FROM + "WHERE i.organizacion_id=:org AND i.evaluacion_id=:evaluation AND i.codigo_externo=:code")
                .param("org", organization).param("evaluation", evaluation).param("code", code).query(this::enrollment).optional();
    }
    public Enrollment require(long organization, long evaluation, long id) {
        return db.sql(ENROLLMENT_SELECT + ENROLLMENT_FROM + "WHERE i.organizacion_id=:org AND i.evaluacion_id=:evaluation AND i.id=:id")
                .param("org", organization).param("evaluation", evaluation).param("id", id).query(this::enrollment).optional()
                .orElseThrow(() -> ApiException.notFound("Inscripción"));
    }
    public Optional<Long> registrationOf(long evaluation, long person) {
        return db.sql("SELECT id FROM inscripciones WHERE evaluacion_id=:evaluation AND participante_id=:person")
                .param("evaluation", evaluation).param("person", person).query(Long.class).optional();
    }
    public long register(long organization, long evaluation, long person, String code, Long group) {
        return db.sql("INSERT INTO inscripciones(organizacion_id,evaluacion_id,participante_id,codigo_externo,grupo_id) VALUES(:org,:evaluation,:person,:code,:group) RETURNING id")
                .param("org", organization).param("evaluation", evaluation).param("person", person).param("code", code)
                .param("group", group).query(Long.class).single();
    }
    public void updateEnrollment(long organization, long evaluation, long id, long person, String code, Long group) {
        db.sql("UPDATE inscripciones SET participante_id=:person,codigo_externo=:code,grupo_id=:group WHERE organizacion_id=:org AND evaluacion_id=:evaluation AND id=:id")
                .param("org", organization).param("evaluation", evaluation).param("id", id).param("person", person)
                .param("code", code).param("group", group).update();
    }
    public PageResult<Enrollment> page(long organization, long evaluation, String query, Long group,
            String profile, String document, int page, int size) {
        String where = " WHERE i.organizacion_id=:org AND i.evaluacion_id=:evaluation "
                + "AND concat_ws(' ',i.codigo_externo,p.nombres,p.apellidos,p.documento,p.correo) ILIKE :query "
                + "AND (CAST(:group AS bigint) IS NULL OR (:group=0 AND i.grupo_id IS NULL) OR i.grupo_id=:group) "
                + "AND (:profile='' OR (:profile='AUTOMATICOS' AND p.registro_automatico) OR (:profile='COMPLETOS' AND NOT p.registro_automatico) OR (:profile='SIN_CORREO' AND nullif(trim(p.correo),'') IS NULL)) "
                + "AND (:document='' OR (:document='CON_DOCUMENTO' AND nullif(trim(p.documento),'') IS NOT NULL) OR (:document='SIN_DOCUMENTO' AND nullif(trim(p.documento),'') IS NULL))";
        String search = "%" + query.trim() + "%";
        long total = db.sql("SELECT count(*)" + ENROLLMENT_FROM + where).param("org", organization).param("evaluation", evaluation)
                .param("query", search).param("group", group).param("profile", profile).param("document", document).query(Long.class).single();
        var items = db.sql(ENROLLMENT_SELECT + ENROLLMENT_FROM + where + " ORDER BY i.codigo_externo,i.id LIMIT :size OFFSET :offset")
                .param("org", organization).param("evaluation", evaluation).param("query", search).param("group", group)
                .param("profile", profile).param("document", document).param("size", size).param("offset", page * size).query(this::enrollment).list();
        return PageResult.of(items, page, size, total);
    }
    public Summary summary(long evaluation) {
        return db.sql("SELECT count(*),count(*) FILTER(WHERE p.registro_automatico),count(*) FILTER(WHERE i.grupo_id IS NULL),count(*) FILTER(WHERE nullif(trim(p.correo),'') IS NULL)"
                + ENROLLMENT_FROM + " WHERE i.evaluacion_id=:evaluation").param("evaluation", evaluation)
                .query((rs, row) -> new Summary(rs.getLong(1), rs.getLong(2), rs.getLong(3), rs.getLong(4))).single();
    }
    private Person person(ResultSet rs, int row) throws SQLException {
        return new Person(rs.getLong("id"), rs.getString("nombres"), rs.getString("apellidos"), rs.getString("tipo_documento"),
                rs.getString("documento"), rs.getString("correo"), rs.getBoolean("registro_automatico"), rs.getLong("registrations"));
    }
    private Enrollment enrollment(ResultSet rs, int row) throws SQLException {
        return new Enrollment(rs.getLong("id"), rs.getLong("person_id"), rs.getString("codigo_externo"), rs.getString("nombres"),
                rs.getString("apellidos"), rs.getString("tipo_documento"), rs.getString("documento"), rs.getString("correo"),
                rs.getBoolean("registro_automatico"), (Long) rs.getObject("grupo_id"), rs.getString("group_name"), rs.getBoolean("has_exam"));
    }
}
