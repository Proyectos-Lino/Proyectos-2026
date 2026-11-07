package com.admision.lector_dbf.evaluation.repository;

import com.admision.lector_dbf.evaluation.model.ScoringModels.Section;
import com.admision.lector_dbf.evaluation.model.ScoringModels.SectionConfiguration;
import com.admision.lector_dbf.evaluation.model.ScoringRequests.SectionInput;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class SectionConfigurationRepository {
    private final JdbcClient db;
    public SectionConfigurationRepository(JdbcClient db) { this.db = db; }

    public SectionConfiguration save(long formId, List<SectionInput> sections, long userId) {
        long id = db.sql("INSERT INTO configuraciones_seccion(formulario_id,version,creada_por) "
                + "SELECT :form,coalesce(max(version),0)+1,:user FROM configuraciones_seccion "
                + "WHERE formulario_id=:form RETURNING id")
                .param("form", formId).param("user", userId).query(Long.class).single();
        for (int index = 0; index < sections.size(); index++) {
            SectionInput section = sections.get(index);
            db.sql("INSERT INTO secciones_configuracion(configuracion_id,orden,codigo,nombre,pregunta_desde,pregunta_hasta,"
                    + "puntos_correcta,puntos_incorrecta,puntos_blanca) "
                    + "VALUES (:configuration,:order,:code,:name,:fromQuestion,:toQuestion,:correct,:incorrect,:blank)")
                    .param("configuration", id).param("order", index + 1).param("code", section.code().trim())
                    .param("name", section.name().trim()).param("fromQuestion", section.fromQuestion())
                    .param("toQuestion", section.toQuestion()).param("correct", section.correct())
                    .param("incorrect", section.incorrect()).param("blank", section.blank()).update();
        }
        return latest(formId).filter(configuration -> configuration.id() == id).orElseThrow();
    }

    public Optional<SectionConfiguration> latest(long formId) {
        return db.sql("SELECT id,formulario_id,version FROM configuraciones_seccion "
                + "WHERE formulario_id=:form ORDER BY version DESC LIMIT 1")
                .param("form", formId)
                .query((rs, row) -> new SectionConfiguration(rs.getLong(1), rs.getLong(2), rs.getInt(3),
                        sections(rs.getLong(1)))).optional();
    }

    private List<Section> sections(long configurationId) {
        return db.sql("SELECT id,orden,codigo,nombre,pregunta_desde,pregunta_hasta,puntos_correcta,puntos_incorrecta,puntos_blanca "
                + "FROM secciones_configuracion WHERE configuracion_id=:configuration ORDER BY orden")
                .param("configuration", configurationId)
                .query((rs, row) -> new Section(rs.getLong(1), rs.getInt(2), rs.getString(3), rs.getString(4),
                        rs.getInt(5), rs.getInt(6), rs.getBigDecimal(7), rs.getBigDecimal(8), rs.getBigDecimal(9))).list();
    }
}
