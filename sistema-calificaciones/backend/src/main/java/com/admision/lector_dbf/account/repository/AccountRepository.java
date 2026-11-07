package com.admision.lector_dbf.account.repository;

import com.admision.lector_dbf.account.model.AccountModels.*;
import com.admision.lector_dbf.shared.PageResult;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class AccountRepository {
    private final JdbcClient db;

    public AccountRepository(JdbcClient db) { this.db = db; }

    public Optional<User> findByEmail(String email) {
        return db.sql("SELECT id, correo, password_hash, nombre, activo FROM usuarios WHERE lower(trim(correo)) = lower(trim(:email))")
                .param("email", email).query((rs, row) -> new User(rs.getLong(1), rs.getString(2), rs.getString(3), rs.getString(4), rs.getBoolean(5)))
                .optional();
    }

    public long userCount() {
        return db.sql("SELECT count(*) FROM usuarios").query(Long.class).single();
    }

    public void lockBootstrap() {
        db.sql("SELECT pg_advisory_xact_lock(730214), 1").query((rs, row) -> rs.getInt(2)).single();
    }

    public long createOrganization(String code, String name) {
        return db.sql("INSERT INTO organizaciones(codigo,nombre) VALUES (:code,:name) RETURNING id")
                .param("code", code).param("name", name).query(Long.class).single();
    }

    public long createUser(String email, String hash, String name) {
        return db.sql("INSERT INTO usuarios(correo,password_hash,nombre) VALUES (:email,:hash,:name) RETURNING id")
                .param("email", email).param("hash", hash).param("name", name).query(Long.class).single();
    }

    public void createMembership(long organizationId, long userId, String role) {
        db.sql("INSERT INTO membresias(organizacion_id,usuario_id,rol) VALUES (:organization,:user,:role)")
                .param("organization", organizationId).param("user", userId).param("role", role).update();
    }

    public Optional<Membership> membership(long organizationId, long userId) {
        return db.sql("SELECT m.organizacion_id,m.usuario_id,m.rol,m.activa FROM membresias m JOIN organizaciones o ON o.id=m.organizacion_id WHERE m.organizacion_id=:organization AND m.usuario_id=:user AND o.activa")
                .param("organization", organizationId).param("user", userId)
                .query((rs, row) -> new Membership(rs.getLong(1), rs.getLong(2), rs.getString(3), rs.getBoolean(4))).optional();
    }

    public Optional<Organization> organization(long organizationId, long userId) {
        return db.sql("SELECT o.id,o.codigo,o.nombre,m.rol FROM organizaciones o JOIN membresias m ON m.organizacion_id=o.id WHERE o.id=:organization AND m.usuario_id=:user AND m.activa AND o.activa")
                .param("organization", organizationId).param("user", userId)
                .query((rs, row) -> new Organization(rs.getLong(1), rs.getString(2), rs.getString(3), rs.getString(4))).optional();
    }

    public int updateOrganization(long organizationId, String name) {
        return db.sql("UPDATE organizaciones SET nombre=:name WHERE id=:organization AND activa")
                .param("name", name).param("organization", organizationId).update();
    }

    public int updateUser(long userId, String email, String name, String passwordHash) {
        return db.sql("UPDATE usuarios SET correo=:email,nombre=:name,"
                        + "password_hash=coalesce(:passwordHash,password_hash) WHERE id=:user")
                .param("email", email).param("name", name).param("passwordHash", passwordHash)
                .param("user", userId).update();
    }

    public List<Organization> organizationsFor(long userId) {
        return db.sql("SELECT o.id,o.codigo,o.nombre,m.rol FROM organizaciones o JOIN membresias m ON m.organizacion_id=o.id WHERE m.usuario_id=:user AND m.activa AND o.activa ORDER BY o.nombre")
                .param("user", userId).query((rs, row) -> new Organization(rs.getLong(1), rs.getString(2), rs.getString(3), rs.getString(4))).list();
    }

    public List<UserView> users(long organizationId) {
        return db.sql("SELECT u.id,u.correo,u.nombre,u.activo,m.rol,m.activa,(SELECT count(*)>1 FROM membresias mx WHERE mx.usuario_id=u.id) FROM usuarios u JOIN membresias m ON m.usuario_id=u.id WHERE m.organizacion_id=:organization ORDER BY u.nombre")
                .param("organization", organizationId).query((rs, row) -> new UserView(rs.getLong(1), rs.getString(2), rs.getString(3), rs.getBoolean(4), rs.getString(5), rs.getBoolean(6), rs.getBoolean(7))).list();
    }

    public PageResult<UserView> usersPage(long organizationId, String query, String role, int page, int size) {
        String filter = " FROM usuarios u JOIN membresias m ON m.usuario_id=u.id "
                + "WHERE m.organizacion_id=:organization AND (:role='' OR m.rol=:role) "
                + "AND (lower(u.nombre) LIKE lower(:query) OR lower(u.correo) LIKE lower(:query))";
        String search = "%" + query.trim() + "%";
        long total = db.sql("SELECT count(*)" + filter).param("organization", organizationId)
                .param("role", role).param("query", search).query(Long.class).single();
        List<UserView> items = db.sql("SELECT u.id,u.correo,u.nombre,u.activo,m.rol,m.activa,(SELECT count(*)>1 FROM membresias mx WHERE mx.usuario_id=u.id)"
                        + filter + " ORDER BY u.nombre,u.id LIMIT :size OFFSET :offset")
                .param("organization", organizationId).param("role", role).param("query", search)
                .param("size", size).param("offset", page * size)
                .query((rs, row) -> new UserView(rs.getLong(1), rs.getString(2), rs.getString(3),
                        rs.getBoolean(4), rs.getString(5), rs.getBoolean(6), rs.getBoolean(7))).list();
        return PageResult.of(items, page, size, total);
    }

    public int updateMembership(long organizationId, long userId, String role, boolean active) {
        return db.sql("UPDATE membresias SET rol=:role,activa=:active WHERE organizacion_id=:organization AND usuario_id=:user")
                .param("role", role).param("active", active).param("organization", organizationId).param("user", userId).update();
    }

    public long activeAdministrators(long organizationId) {
        return db.sql("SELECT count(*) FROM membresias WHERE organizacion_id=:organization AND rol='ADMINISTRADOR' AND activa")
                .param("organization", organizationId).query(Long.class).single();
    }

    public boolean sharedAccount(long userId) {
        return db.sql("SELECT count(*)>1 FROM membresias WHERE usuario_id=:user")
                .param("user", userId).query(Boolean.class).single();
    }
}
