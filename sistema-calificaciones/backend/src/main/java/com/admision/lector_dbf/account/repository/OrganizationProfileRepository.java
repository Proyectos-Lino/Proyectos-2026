package com.admision.lector_dbf.account.repository;

import com.admision.lector_dbf.account.model.*;
import com.admision.lector_dbf.shared.ApiException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public class OrganizationProfileRepository {
    private final JdbcClient db;
    public OrganizationProfileRepository(JdbcClient db) { this.db=db; }

    public OrganizationProfile get(long id) {
        return db.sql("SELECT o.*,EXISTS(SELECT 1 FROM organizaciones_logos l WHERE l.organizacion_id=o.id) AS tiene_logo "
                + "FROM organizaciones o WHERE o.id=:id AND o.activa").param("id",id).query((rs,row) ->
                new OrganizationProfile(rs.getLong("id"),rs.getString("nombre"),rs.getString("codigo"),
                        rs.getString("razon_social"),rs.getString("identificacion_fiscal"),rs.getString("direccion"),
                        rs.getString("ciudad"),rs.getString("pais"),rs.getString("telefono"),rs.getString("correo"),
                        rs.getString("sitio_web"),rs.getString("oficina_responsable"),rs.getString("firmante_nombre"),
                        rs.getString("firmante_cargo"),rs.getBoolean("tiene_logo")))
                .optional().orElseThrow(() -> ApiException.notFound("Organización"));
    }

    public void save(long id,OrganizationProfileInput input) {
        db.sql("UPDATE organizaciones SET razon_social=:legal,identificacion_fiscal=:tax,direccion=:address,"
                + "ciudad=:city,pais=:country,telefono=:phone,correo=:email,sitio_web=:website,"
                + "oficina_responsable=:office,firmante_nombre=:signatory,firmante_cargo=:title WHERE id=:id")
                .param("id",id).param("legal",clean(input.legalName())).param("tax",clean(input.taxId()))
                .param("address",clean(input.address())).param("city",clean(input.city())).param("country",clean(input.country()))
                .param("phone",clean(input.phone())).param("email",clean(input.email())).param("website",clean(input.website()))
                .param("office",clean(input.office())).param("signatory",clean(input.signatoryName())).param("title",clean(input.signatoryTitle())).update();
    }

    public Optional<OrganizationLogo> logo(long id) {
        return db.sql("SELECT contenido,tipo_contenido FROM organizaciones_logos WHERE organizacion_id=:id")
                .param("id",id).query((rs,row) -> new OrganizationLogo(rs.getBytes(1),rs.getString(2))).optional();
    }
    public void saveLogo(long id,byte[] bytes,long user) {
        db.sql("INSERT INTO organizaciones_logos(organizacion_id,contenido,tipo_contenido,actualizada_por) "
                + "VALUES (:id,:bytes,'image/png',:user) ON CONFLICT (organizacion_id) DO UPDATE SET "
                + "contenido=:bytes,tipo_contenido='image/png',actualizada_por=:user,actualizada_en=now()")
                .param("id",id).param("bytes",bytes).param("user",user).update();
    }
    public void deleteLogo(long id) { db.sql("DELETE FROM organizaciones_logos WHERE organizacion_id=:id").param("id",id).update(); }
    private String clean(String value) { return value==null || value.isBlank() ? null : value.trim(); }
}
